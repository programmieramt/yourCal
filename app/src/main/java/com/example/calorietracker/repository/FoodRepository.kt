package com.example.calorietracker.repository

import com.example.calorietracker.data.AppDatabase
import com.example.calorietracker.data.ExerciseEntry
import com.example.calorietracker.data.FavoriteEntry
import com.example.calorietracker.data.FoodEntry
import com.example.calorietracker.data.SessionCompletionEntry
import com.example.calorietracker.data.SettingsStore
import com.example.calorietracker.data.WeightEntry
import com.example.calorietracker.network.ClaudeApi
import com.example.calorietracker.network.ClaudeApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FoodRepository(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
) {
    fun observeEntriesSince(since: Long): Flow<List<FoodEntry>> =
        database.foodDao().observeSince(since)

    fun observeAllEntries(): Flow<List<FoodEntry>> = database.foodDao().observeAll()

    suspend fun addEntry(description: String, timestamp: Long): FoodEntry = withContext(Dispatchers.IO) {
        val apiKey = settingsStore.apiKey
            ?: throw ClaudeApiException("Kein API-Key hinterlegt. Bitte in den Einstellungen eintragen.")

        val estimate = ClaudeApi.estimate(apiKey, description)
        val entry = FoodEntry(
            timestamp = timestamp,
            description = description,
            calories = estimate.calories,
            proteinG = estimate.proteinG,
            carbsG = estimate.carbsG,
            fatG = estimate.fatG,
        )
        val id = database.foodDao().insert(entry)
        entry.copy(id = id)
    }

    suspend fun updateEntry(entry: FoodEntry) = withContext(Dispatchers.IO) {
        database.foodDao().update(entry)
    }

    suspend fun deleteEntry(entry: FoodEntry) = withContext(Dispatchers.IO) {
        database.foodDao().delete(entry)
    }

    /** Legt eine Kopie von [entry] mit neuem Zeitstempel an — gleiche Werte, kein Claude-Call. */
    suspend fun repeatEntry(entry: FoodEntry, timestamp: Long): FoodEntry = withContext(Dispatchers.IO) {
        val copy = entry.copy(id = 0, timestamp = timestamp)
        val id = database.foodDao().insert(copy)
        copy.copy(id = id)
    }

    fun observeFavorites(): Flow<List<FavoriteEntry>> = database.favoriteDao().observeAll()

    suspend fun addFavorite(entry: FoodEntry) = withContext(Dispatchers.IO) {
        database.favoriteDao().insert(
            FavoriteEntry(
                description = entry.description,
                calories = entry.calories,
                proteinG = entry.proteinG,
                carbsG = entry.carbsG,
                fatG = entry.fatG,
            ),
        )
    }

    suspend fun removeFavorite(favorite: FavoriteEntry) = withContext(Dispatchers.IO) {
        database.favoriteDao().delete(favorite)
    }

    suspend fun addEntryFromFavorite(favorite: FavoriteEntry, timestamp: Long): FoodEntry = withContext(Dispatchers.IO) {
        val entry = FoodEntry(
            timestamp = timestamp,
            description = favorite.description,
            calories = favorite.calories,
            proteinG = favorite.proteinG,
            carbsG = favorite.carbsG,
            fatG = favorite.fatG,
        )
        val id = database.foodDao().insert(entry)
        entry.copy(id = id)
    }

    fun observeWeightEntries(): Flow<List<WeightEntry>> = database.weightDao().observeAll()

    suspend fun addWeightEntry(
        weightKg: Double,
        bodyFatPercent: Double? = null,
        muscleMassKg: Double? = null,
    ): WeightEntry = withContext(Dispatchers.IO) {
        val entry = WeightEntry(
            timestamp = System.currentTimeMillis(),
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            muscleMassKg = muscleMassKg,
        )
        val id = database.weightDao().insert(entry)
        entry.copy(id = id)
    }

    suspend fun deleteWeightEntry(entry: WeightEntry) = withContext(Dispatchers.IO) {
        database.weightDao().delete(entry)
    }

    fun observeExerciseSince(since: Long): Flow<List<ExerciseEntry>> =
        database.exerciseDao().observeSince(since)

    fun observeAllExercise(): Flow<List<ExerciseEntry>> = database.exerciseDao().observeAll()

    suspend fun addExerciseEntry(caloriesBurned: Int): ExerciseEntry =
        withContext(Dispatchers.IO) {
            val entry = ExerciseEntry(
                timestamp = System.currentTimeMillis(),
                caloriesBurned = caloriesBurned,
            )
            val id = database.exerciseDao().insert(entry)
            entry.copy(id = id)
        }

    suspend fun deleteExerciseEntry(entry: ExerciseEntry) = withContext(Dispatchers.IO) {
        database.exerciseDao().delete(entry)
    }

    fun observeSessionCompletions(): Flow<List<SessionCompletionEntry>> =
        database.sessionCompletionDao().observeAll()

    suspend fun setSessionCompleted(weekNumber: Int, sessionIndex: Int, completed: Boolean) =
        withContext(Dispatchers.IO) {
            if (completed) {
                database.sessionCompletionDao().setCompleted(
                    SessionCompletionEntry(weekNumber, sessionIndex, System.currentTimeMillis()),
                )
            } else {
                database.sessionCompletionDao().setIncomplete(weekNumber, sessionIndex)
            }
        }

    /** Alle Tabellen als ein JSON-Dokument — für Backup/Export, kein Sync-Format. */
    suspend fun exportAllData(): String = withContext(Dispatchers.IO) {
        val food = database.foodDao().observeAll().first()
        val exercise = database.exerciseDao().observeAll().first()
        val weight = database.weightDao().observeAll().first()
        val favorites = database.favoriteDao().observeAll().first()

        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put(
            "foodEntries",
            JSONArray(
                food.map { e ->
                    JSONObject()
                        .put("id", e.id)
                        .put("timestamp", e.timestamp)
                        .put("description", e.description)
                        .put("calories", e.calories)
                        .put("proteinG", e.proteinG)
                        .put("carbsG", e.carbsG)
                        .put("fatG", e.fatG)
                },
            ),
        )
        root.put(
            "exerciseEntries",
            JSONArray(
                exercise.map { e ->
                    JSONObject()
                        .put("id", e.id)
                        .put("timestamp", e.timestamp)
                        .put("caloriesBurned", e.caloriesBurned)
                },
            ),
        )
        root.put(
            "weightEntries",
            JSONArray(
                weight.map { e ->
                    val o = JSONObject()
                        .put("id", e.id)
                        .put("timestamp", e.timestamp)
                        .put("weightKg", e.weightKg)
                    e.bodyFatPercent?.let { o.put("bodyFatPercent", it) }
                    e.muscleMassKg?.let { o.put("muscleMassKg", it) }
                    o
                },
            ),
        )
        root.put(
            "favorites",
            JSONArray(
                favorites.map { e ->
                    JSONObject()
                        .put("id", e.id)
                        .put("description", e.description)
                        .put("calories", e.calories)
                        .put("proteinG", e.proteinG)
                        .put("carbsG", e.carbsG)
                        .put("fatG", e.fatG)
                },
            ),
        )
        root.toString(2)
    }

    /**
     * Liest ein Export-JSON zurück ein. IDs aus der Datei werden bewusst
     * ignoriert (Room vergibt neue) — das ist ein Restore, kein Merge mit
     * Konflikterkennung; beim Import in eine nicht-leere DB entstehen ggf.
     * Duplikate.
     */
    suspend fun importAllData(json: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        var count = 0

        root.optJSONArray("foodEntries")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                database.foodDao().insert(
                    FoodEntry(
                        timestamp = o.getLong("timestamp"),
                        description = o.getString("description"),
                        calories = o.getInt("calories"),
                        proteinG = o.getDouble("proteinG"),
                        carbsG = o.getDouble("carbsG"),
                        fatG = o.getDouble("fatG"),
                    ),
                )
                count++
            }
        }
        root.optJSONArray("exerciseEntries")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                database.exerciseDao().insert(
                    ExerciseEntry(
                        timestamp = o.getLong("timestamp"),
                        caloriesBurned = o.getInt("caloriesBurned"),
                    ),
                )
                count++
            }
        }
        root.optJSONArray("weightEntries")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                database.weightDao().insert(
                    WeightEntry(
                        timestamp = o.getLong("timestamp"),
                        weightKg = o.getDouble("weightKg"),
                        bodyFatPercent = if (o.has("bodyFatPercent")) o.getDouble("bodyFatPercent") else null,
                        muscleMassKg = if (o.has("muscleMassKg")) o.getDouble("muscleMassKg") else null,
                    ),
                )
                count++
            }
        }
        root.optJSONArray("favorites")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                database.favoriteDao().insert(
                    FavoriteEntry(
                        description = o.getString("description"),
                        calories = o.getInt("calories"),
                        proteinG = o.getDouble("proteinG"),
                        carbsG = o.getDouble("carbsG"),
                        fatG = o.getDouble("fatG"),
                    ),
                )
                count++
            }
        }
        count
    }
}
