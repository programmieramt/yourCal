package com.example.calorietracker.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calorietracker.data.AppDatabase
import com.example.calorietracker.data.ExerciseEntry
import com.example.calorietracker.data.FavoriteEntry
import com.example.calorietracker.data.FoodEntry
import com.example.calorietracker.data.SettingsStore
import com.example.calorietracker.data.WeightEntry
import com.example.calorietracker.repository.FoodRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val NOON_OFFSET_MILLIS = 12 * 60 * 60 * 1000L

data class WeekSummary(
    val totalCalories: Int = 0,
    val totalExerciseCalories: Int = 0,
    val totalProteinG: Double = 0.0,
    val totalCarbsG: Double = 0.0,
    val totalFatG: Double = 0.0,
    val goalCalories: Int = com.example.calorietracker.data.DEFAULT_WEEKLY_GOAL_CALORIES,
) {
    /** Gegessen minus durch Sport verbrannt — das zählt fürs Wochenziel. */
    val netCalories: Int get() = totalCalories - totalExerciseCalories
    val remainingCalories: Int get() = goalCalories - netCalories
    val progress: Float
        get() = if (goalCalories <= 0) 0f else (netCalories.toFloat() / goalCalories).coerceIn(0f, 1.5f)
    val dailyTargetCalories: Int get() = goalCalories / 7
}

data class DayCalories(
    val label: String,
    val calories: Int,
    val isToday: Boolean,
)

data class DayEntries(
    val dayStart: Long,
    val label: String,
    val foodCalories: Int,
    val exerciseCalories: Int,
    val entries: List<FoodEntry>,
    val exerciseEntries: List<ExerciseEntry>,
) {
    val netCalories: Int get() = foodCalories - exerciseCalories
}

/** Ein Kalenderwochen-Punkt (Montag-Start) für den Historie-Trend — unabhängig vom rollierenden Wochenziel-Fenster. */
data class WeeklyPoint(
    val weekStart: Long,
    val label: String,
    val netCalories: Int,
    val avgWeightKg: Double?,
)

/** Ein wählbarer Tag für die Essensplanung: heute oder einer der nächsten 6 Tage. */
data class PlanDayOption(
    val dayStart: Long,
    val label: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val settingsStore = SettingsStore(application)
    private val repository = FoodRepository(AppDatabase.getInstance(application), settingsStore)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Mitternacht des Tages vor [daysAgo] Tagen (0 = heute). */
    private fun startOfDay(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Rollierendes 7-Tage-Fenster (heute + 6 Tage zurück, ab Mitternacht). */
    private fun startOfRollingWeek(): Long = startOfDay(6)

    val weekEntries: StateFlow<List<FoodEntry>> = repository
        .observeEntriesSince(startOfRollingWeek())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exerciseEntries: StateFlow<List<ExerciseEntry>> = repository
        .observeExerciseSince(startOfRollingWeek())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekSummary: StateFlow<WeekSummary> = combine(
        weekEntries,
        exerciseEntries,
        settingsStore.weeklyGoalFlow,
    ) { entries, exercise, goal ->
        // Für zukünftige Tage vorgeplante Einträge zählen erst mit, wenn ihr Tag
        // wirklich erreicht ist — sonst würde die Bilanz Dinge zeigen, die noch
        // gar nicht gegessen wurden.
        val consumed = entries.filter { it.timestamp <= System.currentTimeMillis() }
        WeekSummary(
            totalCalories = consumed.sumOf { it.calories },
            totalExerciseCalories = exercise.sumOf { it.caloriesBurned },
            totalProteinG = consumed.sumOf { it.proteinG },
            totalCarbsG = consumed.sumOf { it.carbsG },
            totalFatG = consumed.sumOf { it.fatG },
            goalCalories = goal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekSummary())

    private val dayLabelFormat = SimpleDateFormat("EEE", Locale.GERMAN)
    private val dayHeaderFormat = SimpleDateFormat("EEEE, dd.MM.", Locale.GERMAN)

    /** Mitternacht des Tages, an dem [timestamp] liegt. */
    private fun dayStartOf(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Netto-Kalorien (gegessen minus Sport) pro Tag im rollierenden 7-Tage-Fenster, älteste zuerst. */
    val dailyCalories: StateFlow<List<DayCalories>> = combine(weekEntries, exerciseEntries) { entries, exercise ->
        (6 downTo 0).map { daysAgo ->
            val dayStart = startOfDay(daysAgo)
            val dayEnd = dayStart + DAY_MILLIS
            val food = entries
                .filter { it.timestamp in dayStart until dayEnd }
                .sumOf { it.calories }
            val burned = exercise
                .filter { it.timestamp in dayStart until dayEnd }
                .sumOf { it.caloriesBurned }
            DayCalories(
                label = dayLabelFormat.format(Date(dayStart)),
                calories = food - burned,
                isToday = daysAgo == 0,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun dayHeaderLabel(dayStart: Long): String = when (dayStart) {
        startOfDay(0) -> "Heute"
        startOfDay(1) -> "Gestern"
        else -> dayHeaderFormat.format(Date(dayStart))
    }

    private fun groupByDay(food: List<FoodEntry>, exercise: List<ExerciseEntry>): List<DayEntries> {
        val foodByDay = food.groupBy { dayStartOf(it.timestamp) }
        val exerciseByDay = exercise.groupBy { dayStartOf(it.timestamp) }
        return (foodByDay.keys + exerciseByDay.keys)
            .sortedDescending()
            .map { dayStart ->
                val dayFood = foodByDay[dayStart].orEmpty()
                val dayExercise = exerciseByDay[dayStart].orEmpty()
                DayEntries(
                    dayStart = dayStart,
                    label = dayHeaderLabel(dayStart),
                    foodCalories = dayFood.sumOf { it.calories },
                    exerciseCalories = dayExercise.sumOf { it.caloriesBurned },
                    entries = dayFood,
                    exerciseEntries = dayExercise,
                )
            }
    }

    /** Einträge der letzten 7 Tage, nach Tag gruppiert (neuester Tag zuerst). */
    val entriesByDay: StateFlow<List<DayEntries>> = combine(weekEntries, exerciseEntries) { entries, exercise ->
        groupByDay(entries, exercise)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Alle jemals erfassten Einträge, nach Tag gruppiert (neuester Tag zuerst) — für die Historie. */
    val historyByDay: StateFlow<List<DayEntries>> = combine(
        repository.observeAllEntries(),
        repository.observeAllExercise(),
    ) { entries, exercise ->
        groupByDay(entries, exercise)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val weekLabelFormat = SimpleDateFormat("dd.MM.", Locale.GERMAN)

    /** Montag 00:00 der Kalenderwoche, in der [timestamp] liegt. */
    private fun mondayStartOf(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // SUNDAY=1 .. SATURDAY=7
        val daysSinceMonday = (dayOfWeek - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        return cal.timeInMillis
    }

    /** Netto-Kalorien und Ø-Gewicht pro Kalenderwoche, älteste zuerst — für den Historie-Trend. */
    val weeklyTrend: StateFlow<List<WeeklyPoint>> = combine(
        repository.observeAllEntries(),
        repository.observeAllExercise(),
        repository.observeWeightEntries(),
    ) { entries, exercise, weights ->
        val consumed = entries.filter { it.timestamp <= System.currentTimeMillis() }
        val foodByWeek = consumed.groupBy { mondayStartOf(it.timestamp) }
        val exerciseByWeek = exercise.groupBy { mondayStartOf(it.timestamp) }
        val weightByWeek = weights.groupBy { mondayStartOf(it.timestamp) }
        (foodByWeek.keys + exerciseByWeek.keys + weightByWeek.keys)
            .sorted()
            .map { weekStart ->
                val weekFood = foodByWeek[weekStart].orEmpty().sumOf { it.calories }
                val weekBurned = exerciseByWeek[weekStart].orEmpty().sumOf { it.caloriesBurned }
                val weekWeights = weightByWeek[weekStart].orEmpty()
                WeeklyPoint(
                    weekStart = weekStart,
                    label = weekLabelFormat.format(Date(weekStart)),
                    netCalories = weekFood - weekBurned,
                    avgWeightKg = if (weekWeights.isEmpty()) null else weekWeights.map { it.weightKg }.average(),
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Heute plus die nächsten 6 Tage, zur Auswahl beim Essen-Planen. */
    fun planDayOptions(): List<PlanDayOption> = (0..6).map { daysAhead ->
        // startOfDay() zählt Tage zurück; ein negatives Argument zählt entsprechend vorwärts.
        val dayStart = startOfDay(-daysAhead)
        PlanDayOption(
            dayStart = dayStart,
            label = if (daysAhead == 0) "Heute" else dayLabelFormat.format(Date(dayStart)),
        )
    }

    /** "Jetzt" für heute, sonst Mittag des geplanten Tages (es ist ja noch nichts passiert). */
    private fun timestampFor(targetDayStart: Long): Long =
        if (targetDayStart == startOfDay(0)) System.currentTimeMillis() else targetDayStart + NOON_OFFSET_MILLIS

    fun addEntry(description: String, targetDayStart: Long = startOfDay(0)) {
        if (description.isBlank()) return
        val timestamp = timestampFor(targetDayStart)
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.addEntry(description.trim(), timestamp)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unbekannter Fehler"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateEntry(entry: FoodEntry) {
        viewModelScope.launch { repository.updateEntry(entry) }
    }

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch { repository.deleteEntry(entry) }
    }

    fun repeatEntry(entry: FoodEntry) {
        viewModelScope.launch {
            try {
                repository.repeatEntry(entry, System.currentTimeMillis())
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unbekannter Fehler"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    val favorites: StateFlow<List<FavoriteEntry>> = repository
        .observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFavorite(entry: FoodEntry) {
        viewModelScope.launch { repository.addFavorite(entry) }
    }

    fun removeFavorite(favorite: FavoriteEntry) {
        viewModelScope.launch { repository.removeFavorite(favorite) }
    }

    fun addFromFavorite(favorite: FavoriteEntry, targetDayStart: Long = startOfDay(0)) {
        val timestamp = timestampFor(targetDayStart)
        viewModelScope.launch {
            try {
                repository.addEntryFromFavorite(favorite, timestamp)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unbekannter Fehler"
            }
        }
    }

    val weightEntries: StateFlow<List<WeightEntry>> = repository
        .observeWeightEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWeightEntry(weightKg: Double) {
        viewModelScope.launch { repository.addWeightEntry(weightKg) }
    }

    fun deleteWeightEntry(entry: WeightEntry) {
        viewModelScope.launch { repository.deleteWeightEntry(entry) }
    }

    fun addExerciseEntry(caloriesBurned: Int) {
        if (caloriesBurned <= 0) return
        viewModelScope.launch { repository.addExerciseEntry(caloriesBurned) }
    }

    fun deleteExerciseEntry(entry: ExerciseEntry) {
        viewModelScope.launch { repository.deleteExerciseEntry(entry) }
    }

    /** Exportiert alle Daten als JSON-Datei im Cache und liefert eine teilbare content://-Uri. */
    suspend fun exportToFile(): Uri = withContext(Dispatchers.IO) {
        val json = repository.exportAllData()
        val context = getApplication<Application>()
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.GERMAN).format(Date())
        val file = File(exportsDir, "calorietracker_export_$timestamp.json")
        file.writeText(json)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
