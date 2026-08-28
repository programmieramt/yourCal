package com.example.calorietracker.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calorietracker.data.AppDatabase
import com.example.calorietracker.data.FavoriteEntry
import com.example.calorietracker.data.FoodEntry
import com.example.calorietracker.data.LiveCalorieTarget
import com.example.calorietracker.data.PlanWeek
import com.example.calorietracker.data.RecoveryModel
import com.example.calorietracker.data.RecoveryState
import com.example.calorietracker.data.SettingsStore
import com.example.calorietracker.data.TrainingPlan
import com.example.calorietracker.data.WeightEntry
import com.example.calorietracker.data.calculateLiveTarget
import com.example.calorietracker.network.IntervalsIcuApi
import com.example.calorietracker.repository.FoodRepository
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val NOON_OFFSET_MILLIS = 12 * 60 * 60 * 1000L

data class WeekSummary(
    val totalCalories: Int = 0,
    val totalProteinG: Double = 0.0,
    val totalCarbsG: Double = 0.0,
    val totalFatG: Double = 0.0,
    val goalCalories: Int = com.example.calorietracker.data.DEFAULT_WEEKLY_GOAL_CALORIES,
) {
    val netCalories: Int get() = totalCalories
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
    val entries: List<FoodEntry>,
) {
    val netCalories: Int get() = foodCalories
}

/** Ein Kalenderwochen-Punkt (Montag-Start) für den Historie-Trend — unabhängig vom rollierenden Wochenziel-Fenster. */
data class WeeklyPoint(
    val weekStart: Long,
    val label: String,
    val netCalories: Int,
    val avgWeightKg: Double?,
    /** Ø-Körperfettanteil der Woche, nur aus Einträgen mit erfasstem Wert — reiner Trend, kein Ziel. */
    val avgBodyFatPercent: Double? = null,
    /** Ø-Muskelmasse laut Waage, direkt gemittelt — nicht dasselbe wie avgLeanMassKg. */
    val avgMuscleMassKg: Double? = null,
) {
    /** rollingWeightKg * rollingBodyFatPercent / 100 — siehe bodyComposition.derivedMetrics im Trainingsplan. */
    val avgFatMassKg: Double?
        get() = if (avgWeightKg != null && avgBodyFatPercent != null) avgWeightKg * avgBodyFatPercent / 100 else null

    /** rollingWeightKg - fatMassKg — "alles außer Fett" (Muskeln, Knochen, Organe, Wasser). */
    val avgLeanMassKg: Double?
        get() {
            val weight = avgWeightKg ?: return null
            val fatMass = avgFatMassKg ?: return null
            return weight - fatMass
        }
}

/** Ein wählbarer Tag für die Essensplanung: heute oder einer der nächsten 6 Tage. */
data class PlanDayOption(
    val dayStart: Long,
    val label: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val settingsStore = SettingsStore(application)
    private val repository = FoodRepository(AppDatabase.getInstance(application), settingsStore, application)

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

    // Tickt regelmäßig, damit das rollierende Fenster auch ohne neuen Eintrag
    // aktuell bleibt. Ohne das würde die "since"-Grenze unten nur einmal beim
    // Erzeugen des ViewModels berechnet und nie wieder aktualisiert — bleibt
    // die App-Instanz länger am Leben (z.B. weil das Home-Widget die Activity
    // per singleTop wiederverwendet statt neu zu starten), rutscht das Fenster
    // dann nicht mehr mit und die Wochensumme zieht stillschweigend zu alte
    // Tage mit rein, die die Tages-Balken (die ihre Grenzen jedes Mal frisch
    // bestimmen) schon korrekt rausgefiltert haben.
    private val rollingWindowTick: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(5 * 60 * 1000L)
        }
    }

    // Aus der Leistungsdiagnostik Uni Potsdam, 27.05.2026 — Basis für die
    // Load-Berechnung im Recovery-Modell (siehe RecoveryModel.loadFrom).
    private val recoveryHrMax = 191.0

    /**
     * Recovery-Ampel (Fitness/Fatigue/Form nach Banister). Synct höchstens einmal
     * pro Kalendertag mit intervals.icu (getriggert vom ohnehin laufenden
     * rollingWindowTick) und schreibt CTL/ATL Tag für Tag fort, auch über
     * mehrtägige Lücken seit dem letzten App-Start hinweg. Ohne hinterlegte
     * intervals.icu-Zugangsdaten bleibt der Wert null.
     */
    val recoveryState: StateFlow<RecoveryState?> = flow {
        while (true) {
            emit(syncRecoveryState())
            delay(5 * 60 * 1000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun syncRecoveryState(): RecoveryState? {
        val apiKey = settingsStore.intervalsApiKey ?: return null
        val athleteId = settingsStore.intervalsAthleteId ?: return null

        val current = RecoveryState(
            date = LocalDate.parse(settingsStore.recoveryDate),
            ctl = settingsStore.recoveryCtl,
            atl = settingsStore.recoveryAtl,
        )
        val today = LocalDate.now()
        if (!current.date.isBefore(today)) return current

        val activities = withContext(Dispatchers.IO) {
            runCatching {
                IntervalsIcuApi.fetchActivities(athleteId, apiKey, current.date.plusDays(1), today)
            }.getOrNull()
        } ?: return current // Netzwerkfehler o.ä. — beim naechsten Tick erneut versuchen

        val loadByDate = activities
            .groupBy { it.date }
            .mapValues { (_, acts) -> acts.sumOf { RecoveryModel.loadFrom(it.durationMin, it.avgHr, recoveryHrMax) } }
        val updated = RecoveryModel.advance(current, today, loadByDate)

        settingsStore.recoveryCtl = updated.ctl
        settingsStore.recoveryAtl = updated.atl
        settingsStore.recoveryDate = updated.date.toString()
        return updated
    }

    val weekEntries: StateFlow<List<FoodEntry>> = combine(
        repository.observeAllEntries(),
        rollingWindowTick,
        settingsStore.weekResetAtFlow,
    ) { all, _, resetAt -> all.filter { it.timestamp >= maxOf(startOfRollingWeek(), resetAt) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Setzt nur die aktuelle Wochenbilanz zurück — löscht keine Einträge, verschiebt
     * lediglich die "since"-Grenze für weekEntries/exerciseEntries auf jetzt. Ältere
     * Einträge bleiben unverändert in der Historie sichtbar.
     */
    fun resetWeek() {
        settingsStore.weekResetAt = System.currentTimeMillis()
    }

    /** Statischer Trainings-/Kalorienplan aus assets/training_plan.json, falls vorhanden. */
    val trainingPlan: TrainingPlan? by lazy { TrainingPlan.loadFromAssets(application) }

    /** Ø-Gewicht der letzten 7 Wiege-Einträge (nicht Kalendertage) — Basis für die Live-Kalorienberechnung. */
    private val rollingWeightKg: StateFlow<Double?> = repository.observeWeightEntries()
        .map { entries -> entries.take(7).map { it.weightKg }.takeIf { it.isNotEmpty() }?.average() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Die Trainingsplan-Woche, in der "heute" liegt (null außerhalb des Plan-Zeitraums oder ohne Plan). */
    val currentPlanWeek: StateFlow<PlanWeek?> = rollingWindowTick
        .map { trainingPlan?.weekFor(LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Live berechnetes Kalorienziel nach liveRecalculation aus dem Trainingsplan
     * (siehe TrainingPlan.calculateLiveTarget) — ersetzt das manuelle Wochenziel
     * aus den Einstellungen, sobald Gewichtsdaten und eine aktive Planwoche
     * vorliegen. Ohne Gewichtseintrag oder außerhalb des Plan-Zeitraums bleibt
     * es beim manuellen Ziel (siehe weekSummary unten).
     */
    val liveCalorieTarget: StateFlow<LiveCalorieTarget?> = combine(
        rollingWeightKg,
        currentPlanWeek,
    ) { weightKg, week ->
        val plan = trainingPlan
        if (plan != null && week != null && weightKg != null) {
            plan.calculateLiveTarget(week, weightKg)
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weekSummary: StateFlow<WeekSummary> = combine(
        weekEntries,
        settingsStore.weeklyGoalFlow,
        liveCalorieTarget,
    ) { entries, manualGoal, liveTarget ->
        // Für zukünftige Tage vorgeplante Einträge zählen erst mit, wenn ihr Tag
        // wirklich erreicht ist — sonst würde die Bilanz Dinge zeigen, die noch
        // gar nicht gegessen wurden.
        val consumed = entries.filter { it.timestamp <= System.currentTimeMillis() }
        WeekSummary(
            totalCalories = consumed.sumOf { it.calories },
            totalProteinG = consumed.sumOf { it.proteinG },
            totalCarbsG = consumed.sumOf { it.carbsG },
            totalFatG = consumed.sumOf { it.fatG },
            // Live-Ziel aus dem Trainingsplan hat Vorrang vor dem manuellen
            // Wochenziel, sobald es berechnet werden kann.
            goalCalories = liveTarget?.targetKcalPerWeek ?: manualGoal,
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

    /** Kalorien pro Tag im rollierenden 7-Tage-Fenster, älteste zuerst. */
    val dailyCalories: StateFlow<List<DayCalories>> = weekEntries.map { entries ->
        (6 downTo 0).map { daysAgo ->
            val dayStart = startOfDay(daysAgo)
            val dayEnd = dayStart + DAY_MILLIS
            val food = entries
                .filter { it.timestamp in dayStart until dayEnd }
                .sumOf { it.calories }
            DayCalories(
                label = dayLabelFormat.format(Date(dayStart)),
                calories = food,
                isToday = daysAgo == 0,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Home-Widget-Refresh läuft jetzt direkt in FoodRepository bei jedem
    // Schreibvorgang (siehe dort) — zuverlässiger als ein Flow-Collector hier,
    // der nur lief, solange diese ViewModel-Instanz am Leben war. Bei reiner
    // Widget-Nutzung (App nie aktiv offen gehalten) killt Android den Prozess
    // auf manchen Geräten schnell, wodurch das Widget eingefroren blieb.

    private fun dayHeaderLabel(dayStart: Long): String = when (dayStart) {
        startOfDay(0) -> "Heute"
        startOfDay(1) -> "Gestern"
        else -> dayHeaderFormat.format(Date(dayStart))
    }

    private fun groupByDay(food: List<FoodEntry>): List<DayEntries> {
        val foodByDay = food.groupBy { dayStartOf(it.timestamp) }
        return foodByDay.keys
            .sortedDescending()
            .map { dayStart ->
                val dayFood = foodByDay[dayStart].orEmpty()
                DayEntries(
                    dayStart = dayStart,
                    label = dayHeaderLabel(dayStart),
                    foodCalories = dayFood.sumOf { it.calories },
                    entries = dayFood,
                )
            }
    }

    /** Einträge der letzten 7 Tage, nach Tag gruppiert (neuester Tag zuerst). */
    val entriesByDay: StateFlow<List<DayEntries>> = weekEntries.map { entries ->
        groupByDay(entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Alle jemals erfassten Einträge, nach Tag gruppiert (neuester Tag zuerst) — für die Historie. */
    val historyByDay: StateFlow<List<DayEntries>> = repository.observeAllEntries().map { entries ->
        groupByDay(entries)
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
        repository.observeWeightEntries(),
    ) { entries, weights ->
        val consumed = entries.filter { it.timestamp <= System.currentTimeMillis() }
        val foodByWeek = consumed.groupBy { mondayStartOf(it.timestamp) }
        val weightByWeek = weights.groupBy { mondayStartOf(it.timestamp) }
        (foodByWeek.keys + weightByWeek.keys)
            .sorted()
            .map { weekStart ->
                val weekFood = foodByWeek[weekStart].orEmpty().sumOf { it.calories }
                val weekWeights = weightByWeek[weekStart].orEmpty()
                val bodyFatValues = weekWeights.mapNotNull { it.bodyFatPercent }
                val muscleMassValues = weekWeights.mapNotNull { it.muscleMassKg }
                WeeklyPoint(
                    weekStart = weekStart,
                    label = weekLabelFormat.format(Date(weekStart)),
                    netCalories = weekFood,
                    avgWeightKg = if (weekWeights.isEmpty()) null else weekWeights.map { it.weightKg }.average(),
                    avgBodyFatPercent = bodyFatValues.takeIf { it.isNotEmpty() }?.average(),
                    avgMuscleMassKg = muscleMassValues.takeIf { it.isNotEmpty() }?.average(),
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

    fun renameFavorite(favorite: FavoriteEntry, newDescription: String) {
        if (newDescription.isBlank()) return
        viewModelScope.launch { repository.renameFavorite(favorite, newDescription.trim()) }
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

    fun addWeightEntry(weightKg: Double, bodyFatPercent: Double? = null, muscleMassKg: Double? = null) {
        viewModelScope.launch { repository.addWeightEntry(weightKg, bodyFatPercent, muscleMassKg) }
    }

    fun deleteWeightEntry(entry: WeightEntry) {
        viewModelScope.launch { repository.deleteWeightEntry(entry) }
    }

    /** Abgehakte Trainingsplan-Sessions als (Wochennummer, Session-Index)-Paare. */
    val sessionCompletions: StateFlow<Set<Pair<Int, Int>>> = repository.observeSessionCompletions()
        .map { list -> list.map { it.weekNumber to it.sessionIndex }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setSessionCompleted(weekNumber: Int, sessionIndex: Int, completed: Boolean) {
        viewModelScope.launch { repository.setSessionCompleted(weekNumber, sessionIndex, completed) }
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

    /** Liest eine zuvor exportierte JSON-Datei ein und gibt die Zahl importierter Einträge zurück. */
    suspend fun importFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IllegalStateException("Datei konnte nicht gelesen werden")
        repository.importAllData(json)
    }
}
