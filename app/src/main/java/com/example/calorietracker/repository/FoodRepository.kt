package com.example.calorietracker.repository

import com.example.calorietracker.data.AppDatabase
import com.example.calorietracker.data.FoodEntry
import com.example.calorietracker.data.SettingsStore
import com.example.calorietracker.network.ClaudeApi
import com.example.calorietracker.network.ClaudeApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FoodRepository(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
) {
    fun observeEntriesSince(since: Long): Flow<List<FoodEntry>> =
        database.foodDao().observeSince(since)

    suspend fun addEntry(description: String): FoodEntry = withContext(Dispatchers.IO) {
        val apiKey = settingsStore.apiKey
            ?: throw ClaudeApiException("Kein API-Key hinterlegt. Bitte in den Einstellungen eintragen.")

        val estimate = ClaudeApi.estimate(apiKey, description)
        val entry = FoodEntry(
            timestamp = System.currentTimeMillis(),
            description = description,
            calories = estimate.calories,
            proteinG = estimate.proteinG,
            carbsG = estimate.carbsG,
            fatG = estimate.fatG,
        )
        val id = database.foodDao().insert(entry)
        entry.copy(id = id)
    }

    suspend fun deleteEntry(entry: FoodEntry) = withContext(Dispatchers.IO) {
        database.foodDao().delete(entry)
    }
}
