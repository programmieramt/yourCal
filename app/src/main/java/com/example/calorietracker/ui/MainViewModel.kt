package com.example.calorietracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calorietracker.data.AppDatabase
import com.example.calorietracker.data.FoodEntry
import com.example.calorietracker.data.SettingsStore
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
    val totalProteinG: Double = 0.0,
    val totalCarbsG: Double = 0.0,
    val totalFatG: Double = 0.0,
    val goalCalories: Int = com.example.calorietracker.data.DEFAULT_WEEKLY_GOAL_CALORIES,
) {
    val remainingCalories: Int get() = goalCalories - totalCalories
    val progress: Float
        get() = if (goalCalories <= 0) 0f else (totalCalories.toFloat() / goalCalories).coerceIn(0f, 1.5f)
    val dailyTargetCalories: Int get() = goalCalories / 7
}

data class DayCalories(
    val label: String,
    val calories: Int,
    val isToday: Boolean,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val settingsStore = SettingsStore(application)
    private val repository = FoodRepository(AppDatabase.getInstance(application), settingsStore)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Rollierendes 7-Tage-Fenster (heute + 6 Tage zurück, ab Mitternacht). */
    private fun startOfRollingWeek(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val weekEntries: StateFlow<List<FoodEntry>> = repository
        .observeEntriesSince(startOfRollingWeek())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekSummary: StateFlow<WeekSummary> = combine(
        weekEntries,
        settingsStore.weeklyGoalFlow,
    ) { entries, goal ->
        WeekSummary(
            totalCalories = entries.sumOf { it.calories },
            totalProteinG = entries.sumOf { it.proteinG },
            totalCarbsG = entries.sumOf { it.carbsG },
            totalFatG = entries.sumOf { it.fatG },
            goalCalories = goal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekSummary())

    private val dayLabelFormat = SimpleDateFormat("EEE", Locale.GERMAN)

    /** Kalorien pro Tag im rollierenden 7-Tage-Fenster, älteste zuerst. */
    val dailyCalories: StateFlow<List<DayCalories>> = weekEntries.map { entries ->
        (6 downTo 0).map { daysAgo ->
            val dayCal = Calendar.getInstance()
            dayCal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            dayCal.set(Calendar.HOUR_OF_DAY, 0)
            dayCal.set(Calendar.MINUTE, 0)
            dayCal.set(Calendar.SECOND, 0)
            dayCal.set(Calendar.MILLISECOND, 0)
            val dayStart = dayCal.timeInMillis
            val dayEnd = dayStart + DAY_MILLIS
            val calories = entries
                .filter { it.timestamp in dayStart until dayEnd }
                .sumOf { it.calories }
            DayCalories(
                label = dayLabelFormat.format(Date(dayStart)),
                calories = calories,
                isToday = daysAgo == 0,
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

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch { repository.deleteEntry(entry) }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
