package com.example.calorietracker.network

import java.time.LocalDate
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/** Ein Trainingstag-Datenpunkt für die Recovery-Berechnung — Dauer + Ø-Herzfrequenz. */
data class IntervalsActivity(
    val date: LocalDate,
    val durationMin: Double,
    val avgHr: Double?,
)

class IntervalsIcuException(message: String) : Exception(message)

/**
 * Liest Trainingsaktivitäten von intervals.icu. Basic-Auth mit Username "API_KEY" und
 * dem persönlichen API-Key als Passwort (offizielles Format, siehe
 * https://forum.intervals.icu/t/api-access-to-intervals-icu/609).
 *
 * Nutzt den JSON-Endpoint (ohne ".csv"-Suffix) statt der CSV-Variante — die CSV-Variante
 * hat den oldest/newest-Filter beim Testen ignoriert, der JSON-Endpoint respektiert ihn
 * korrekt und lässt sich ohne eigenen CSV-Parser direkt mit org.json auslesen.
 */
object IntervalsIcuApi {
    private const val BASE_URL = "https://intervals.icu/api/v1/athlete"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Blockierender Aufruf — vom Aufrufer auf einem IO-Dispatcher auszuführen. */
    fun fetchActivities(
        athleteId: String,
        apiKey: String,
        oldest: LocalDate,
        newest: LocalDate,
    ): List<IntervalsActivity> {
        val url = "$BASE_URL/$athleteId/activities?oldest=$oldest&newest=$newest"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", Credentials.basic("API_KEY", apiKey))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string()
                ?: throw IntervalsIcuException("Leere Antwort von intervals.icu")

            if (!response.isSuccessful) {
                throw IntervalsIcuException("intervals.icu-Fehler (HTTP ${response.code})")
            }

            val array = JSONArray(bodyString)
            val result = mutableListOf<IntervalsActivity>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val startDateLocal = o.optString("start_date_local", "")
                if (startDateLocal.length < 10) continue
                val date = runCatching { LocalDate.parse(startDateLocal.substring(0, 10)) }.getOrNull()
                    ?: continue
                val movingTimeSecs = o.optDouble("moving_time", 0.0)
                if (movingTimeSecs <= 0.0) continue
                val avgHr = if (o.has("average_heartrate") && !o.isNull("average_heartrate")) {
                    o.optDouble("average_heartrate").takeIf { !it.isNaN() }
                } else {
                    null
                }
                result.add(IntervalsActivity(date = date, durationMin = movingTimeSecs / 60.0, avgHr = avgHr))
            }
            return result
        }
    }
}
