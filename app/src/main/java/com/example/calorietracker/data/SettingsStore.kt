package com.example.calorietracker.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "secure_settings"
private const val KEY_API_KEY = "api_key"
private const val KEY_WEEKLY_GOAL = "weekly_goal_calories"

// ~2000 kcal/Tag als Startwert für das Wochenziel
const val DEFAULT_WEEKLY_GOAL_CALORIES = 14000

/**
 * Speichert API-Key und Ziele verschlüsselt auf dem Gerät (EncryptedSharedPreferences).
 * Der API-Key verlässt das Gerät nie außer direkt an api.anthropic.com.
 */
class SettingsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _apiKeyFlow = MutableStateFlow(prefs.getString(KEY_API_KEY, null))
    val apiKeyFlow: StateFlow<String?> = _apiKeyFlow.asStateFlow()

    private val _weeklyGoalFlow = MutableStateFlow(
        prefs.getInt(KEY_WEEKLY_GOAL, DEFAULT_WEEKLY_GOAL_CALORIES),
    )
    val weeklyGoalFlow: StateFlow<Int> = _weeklyGoalFlow.asStateFlow()

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value?.trim()?.ifBlank { null }).apply()
            _apiKeyFlow.value = prefs.getString(KEY_API_KEY, null)
        }

    var weeklyGoalCalories: Int
        get() = prefs.getInt(KEY_WEEKLY_GOAL, DEFAULT_WEEKLY_GOAL_CALORIES)
        set(value) {
            prefs.edit().putInt(KEY_WEEKLY_GOAL, value).apply()
            _weeklyGoalFlow.value = value
        }
}
