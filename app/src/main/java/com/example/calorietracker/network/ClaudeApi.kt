package com.example.calorietracker.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NutritionEstimate(
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

class ClaudeApiException(message: String) : Exception(message)

/**
 * Ruft die Anthropic Messages API direkt vom Client auf (der Nutzer trägt seinen
 * eigenen API-Key in der App ein). Erzwingt über output_config.format ein festes
 * JSON-Schema, damit die Antwort ohne Prompt-Parsing-Heuristik verarbeitet werden kann.
 */
object ClaudeApi {
    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-opus-5"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    private const val SYSTEM_PROMPT =
        "Du bist ein Ernährungsassistent. Der Nutzer beschreibt eine Mahlzeit in " +
            "Alltagssprache, oft ungenau (z.B. \"150g Reis mit etwas Sojasauce\"). " +
            "Schätze daraus die Gesamtkalorien (kcal) sowie Protein, Kohlenhydrate und " +
            "Fett in Gramm für die GESAMTE beschriebene Portion. Nutze plausible " +
            "Standardwerte aus gängigen Nährwerttabellen, wenn Mengenangaben fehlen " +
            "oder ungenau sind. Antworte ausschließlich mit den geschätzten Werten."

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    private val outputSchema = JSONObject(
        """
        {
          "type": "object",
          "properties": {
            "calories": {"type": "integer", "description": "Gesamtkalorien in kcal"},
            "protein_g": {"type": "number", "description": "Protein in Gramm"},
            "carbs_g": {"type": "number", "description": "Kohlenhydrate in Gramm"},
            "fat_g": {"type": "number", "description": "Fett in Gramm"}
          },
          "required": ["calories", "protein_g", "carbs_g", "fat_g"],
          "additionalProperties": false
        }
        """.trimIndent(),
    )

    /** Blockierender Aufruf — vom Aufrufer auf einem IO-Dispatcher auszuführen. */
    fun estimate(apiKey: String, description: String): NutritionEstimate {
        val requestJson = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 512)
            // Für eine simple Extraktionsaufgabe reicht niedriger Effort und
            // ausgeschaltetes Thinking (spart Kosten/Latenz bei jeder Mahlzeit).
            put("thinking", JSONObject().put("type", "disabled"))
            put(
                "output_config",
                JSONObject().apply {
                    put("effort", "low")
                    put(
                        "format",
                        JSONObject().apply {
                            put("type", "json_schema")
                            put("schema", outputSchema)
                        },
                    )
                },
            )
            put("system", SYSTEM_PROMPT)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", description)
                    },
                ),
            )
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string()
                ?: throw ClaudeApiException("Leere Antwort vom Server")

            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(bodyString).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw ClaudeApiException(
                    message?.takeIf { it.isNotBlank() } ?: "Serverfehler (HTTP ${response.code})",
                )
            }

            val json = JSONObject(bodyString)

            if (json.optString("stop_reason") == "refusal") {
                throw ClaudeApiException("Die Anfrage wurde abgelehnt. Bitte anders formulieren.")
            }

            val content = json.optJSONArray("content")
                ?: throw ClaudeApiException("Unerwartetes Antwortformat")

            var text: String? = null
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") {
                    text = block.optString("text")
                    break
                }
            }
            if (text.isNullOrBlank()) {
                throw ClaudeApiException("Keine Schätzung erhalten")
            }

            val parsed = JSONObject(text)
            return NutritionEstimate(
                calories = parsed.getDouble("calories").toInt(),
                proteinG = parsed.getDouble("protein_g"),
                carbsG = parsed.getDouble("carbs_g"),
                fatG = parsed.getDouble("fat_g"),
            )
        }
    }
}
