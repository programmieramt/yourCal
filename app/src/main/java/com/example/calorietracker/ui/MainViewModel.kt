package com.example.calorietracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calorietracker.data.AppDatabase
import com.example.calorietracker.data.ExerciseEntry
import com.example.calorietracker.data.FavoriteEntry
import com.example.calorietracker.data.FoodEntry
import com.example.calorietracker.data.SettingsStore
import com.example.calorietracker.data.WeightEntry
import com.example.calorietracker.repository.FoodRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

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
        WeekSummary(
            totalCalories = entries.sumOf { it.calories },
            totalExerciseCalories = exercise.sumOf { it.caloriesBurned },
            totalProteinG = entries.sumOf { it.proteinG },
            totalCarbsG = entries.sumOf { it.carbsG },
            totalFatG = entries.sumOf { it.fatG },
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

    /** Einträge der letzten 7 Tage, nach Tag gruppiert (neuester Tag zuerst). */
    val entriesByDay: StateFlow<List<DayEntries>> = combine(weekEntries, exerciseEntries) { entries, exercise ->
        val foodByDay = entries.groupBy { dayStartOf(it.timestamp) }
        val exerciseByDay = exercise.groupBy { dayStartOf(it.timestamp) }
        (foodByDay.keys + exerciseByDay.keys)
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addEntry(description: String) {
        if (description.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.addEntry(description.trim())
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

    fun addFromFavorite(favorite: FavoriteEntry) {
        viewModelScope.launch {
            try {
                repository.addEntryFromFavorite(favorite)
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
}
