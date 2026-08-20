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
private const val KEY_INTERVALS_API_KEY = "intervals_api_key"
private const val KEY_INTERVALS_ATHLETE_ID = "intervals_athlete_id"
private const val KEY_RECOVERY_CTL = "recovery_ctl"
private const val KEY_RECOVERY_ATL = "recovery_atl"
private const val KEY_RECOVERY_DATE = "recovery_date"

// ~2000 kcal/Tag als Startwert für das Wochenziel
const val DEFAULT_WEEKLY_GOAL_CALORIES = 14000

// Startwerte für das Recovery-Modell, aus intervals.icu übernommen (Stand 20.08.2026),
// damit CTL/ATL nicht bei 0 anfangen — siehe RecoveryModel.
const val RECOVERY_SEED_CTL = 12.7
const val RECOVERY_SEED_ATL = 26.3
const val RECOVERY_SEED_DATE = "2026-08-20"

/**
 * Speichert API-Keys, Ziele und den Recovery-Modell-Zustand verschlüsselt auf dem
 * Gerät (EncryptedSharedPreferences). Keys verlassen das Gerät nie außer direkt an
 * die jeweilige API (api.anthropic.com bzw. intervals.icu).
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

    private val _intervalsApiKeyFlow = MutableStateFlow(prefs.getString(KEY_INTERVALS_API_KEY, null))
    val intervalsApiKeyFlow: StateFlow<String?> = _intervalsApiKeyFlow.asStateFlow()

    private val _intervalsAthleteIdFlow = MutableStateFlow(prefs.getString(KEY_INTERVALS_ATHLETE_ID, null))
    val intervalsAthleteIdFlow: StateFlow<String?> = _intervalsAthleteIdFlow.asStateFlow()

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

    var intervalsApiKey: String?
        get() = prefs.getString(KEY_INTERVALS_API_KEY, null)
        set(value) {
            prefs.edit().putString(KEY_INTERVALS_API_KEY, value?.trim()?.ifBlank { null }).apply()
            _intervalsApiKeyFlow.value = prefs.getString(KEY_INTERVALS_API_KEY, null)
        }

    var intervalsAthleteId: String?
        get() = prefs.getString(KEY_INTERVALS_ATHLETE_ID, null)
        set(value) {
            prefs.edit().putString(KEY_INTERVALS_ATHLETE_ID, value?.trim()?.ifBlank { null }).apply()
            _intervalsAthleteIdFlow.value = prefs.getString(KEY_INTERVALS_ATHLETE_ID, null)
        }

    /** Zuletzt persistierter Fitness/Fatigue-Stand des Recovery-Modells, Default = Startwerte. */
    var recoveryCtl: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_RECOVERY_CTL, java.lang.Double.doubleToLongBits(RECOVERY_SEED_CTL)),
        )
        set(value) = prefs.edit().putLong(KEY_RECOVERY_CTL, java.lang.Double.doubleToLongBits(value)).apply()

    var recoveryAtl: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_RECOVERY_ATL, java.lang.Double.doubleToLongBits(RECOVERY_SEED_ATL)),
        )
        set(value) = prefs.edit().putLong(KEY_RECOVERY_ATL, java.lang.Double.doubleToLongBits(value)).apply()

    /** ISO-Datum (yyyy-MM-dd), bis zu dem CTL/ATL zuletzt fortgeschrieben wurden. */
    var recoveryDate: String
        get() = prefs.getString(KEY_RECOVERY_DATE, RECOVERY_SEED_DATE) ?: RECOVERY_SEED_DATE
        set(value) = prefs.edit().putString(KEY_RECOVERY_DATE, value).apply()
}
