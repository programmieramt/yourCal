package com.example.calorietracker.repository

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.example.calorietracker.data.AppDatabase
import com.example.calorietracker.data.ExerciseEntry
import com.example.calorietracker.data.FavoriteEntry
import com.example.calorietracker.data.FoodEntry
import com.example.calorietracker.data.SessionCompletionEntry
import com.example.calorietracker.data.SettingsStore
import com.example.calorietracker.data.WeightEntry
import com.example.calorietracker.network.ClaudeApi
import com.example.calorietracker.network.ClaudeApiException
import com.example.calorietracker.widget.CalorieWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FoodRepository(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val context: Context,
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var refreshWidgetJob: Job? = null

    /**
     * Aktualisiert das Home-Widget bei jedem Schreibvorgang, statt sich auf einen
     * Flow-Collector im ViewModel zu verlassen — der lief nur, solange die
     * App-Instanz am Leben war. Killt Android den Prozess im Hintergrund (bei
     * Widget-Nutzung ohne die App offen zu halten, auf manchen ROMs sehr
     * aggressiv), blieb das Widget sonst eingefroren.
     *
     * Debounced statt bei jedem Aufruf sofort ein Update anzustoßen: Glance
     * queued jeden updateAll()-Aufruf als eigenen WorkManager-Job, und die
     * laufen nicht garantiert in der Reihenfolge fertig, in der sie ausgelöst
     * wurden — bei mehreren Einträgen kurz hintereinander konnte so ein älterer
     * (noch unvollständiger) Stand einen bereits fertigen neueren überschreiben,
     * das jeweils zuletzt Hinzugefügte fehlte dann im Widget. Der Cancel/Restart
     * hier sorgt dafür, dass bei einer Schreib-Serie nur ein einziger Job für den
     * jeweils letzten Stand läuft.
     */
    private fun refreshWidget() {
        refreshWidgetJob?.cancel()
        refreshWidgetJob = repositoryScope.launch {
            delay(800)
            runCatching { CalorieWidget().updateAll(context) }
                .onFailure { Log.e("FoodRepository", "Widget-Refresh fehlgeschlagen", it) }
        }
    }

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
        refreshWidget()
        entry.copy(id = id)
    }

    suspend fun updateEntry(entry: FoodEntry) = withContext(Dispatchers.IO) {
        database.foodDao().update(entry)
        refreshWidget()
    }

    suspend fun deleteEntry(entry: FoodEntry) = withContext(Dispatchers.IO) {
        database.foodDao().delete(entry)
        refreshWidget()
    }

    /** Legt eine Kopie von [entry] mit neuem Zeitstempel an — gleiche Werte, kein Claude-Call. */
    suspend fun repeatEntry(entry: FoodEntry, timestamp: Long): FoodEntry = withContext(Dispatchers.IO) {
        val copy = entry.copy(id = 0, timestamp = timestamp)
        val id = database.foodDao().insert(copy)
        refreshWidget()
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
        refreshWidget()
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
            refreshWidget()
            entry.copy(id = id)
        }

    suspend fun deleteExerciseEntry(entry: ExerciseEntry) = withContext(Dispatchers.IO) {
        database.exerciseDao().delete(entry)
        refreshWidget()
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
        val sessionCompletions = database.sessionCompletionDao().observeAll().first()

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
        root.put(
            "sessionCompletions",
            JSONArray(
                sessionCompletions.map { e ->
                    JSONObject()
                        .put("weekNumber", e.weekNumber)
                        .put("sessionIndex", e.sessionIndex)
                        .put("completedAt", e.completedAt)
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
        root.optJSONArray("sessionCompletions")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                // REPLACE-Konflikt-Strategie in setCompleted() — anders als bei den
                // anderen Tabellen entstehen hier beim erneuten Import keine
                // Duplikate, da (weekNumber, sessionIndex) der Primärschlüssel ist.
                database.sessionCompletionDao().setCompleted(
                    SessionCompletionEntry(
                        weekNumber = o.getInt("weekNumber"),
                        sessionIndex = o.getInt("sessionIndex"),
                        completedAt = o.getLong("completedAt"),
                    ),
                )
                count++
            }
        }
        refreshWidget()
        count
    }
}
