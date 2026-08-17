package com.example.calorietracker.data

import android.content.Context
import java.time.LocalDate
import org.json.JSONObject

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

data class PlanAthlete(
    val name: String,
    val heightCm: Double,
    val ageYears: Int,
)

data class PlanRace(
    val name: String,
    val date: LocalDate,
    val goalTime: String,
    val goalPacePerKm: String,
)

data class PlanCalorieFormula(
    val neatFactor: Double,
    val runningKcalPerKgPerKm: Double,
    val floorKcalPerDay: Int,
    val roundToNearestKcal: Int,
)

data class PlanPhase(
    val id: Int,
    val name: String,
    val weekRange: IntRange,
    val focus: String,
)

data class PlanSession(
    val type: String,
    val kind: String,
    val day: String?,
    val isRest: Boolean,
    val detail: String,
)

data class PlanWeek(
    val week: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val phaseId: Int,
    val phaseName: String,
    val volumeKm: Double,
    val placeholderTargetKcalPerDay: Int,
    val placeholderTargetKcalPerWeek: Int,
    val sessions: List<PlanSession>,
    val milestone: String?,
) {
    fun contains(date: LocalDate): Boolean = date >= startDate && date <= endDate
}

/** Statischer Trainings-/Kalorienplan, gebündelt als App-Asset (kein Nutzer-Import). */
data class TrainingPlan(
    val athlete: PlanAthlete,
    val race: PlanRace,
    val planStartDate: LocalDate,
    val calorieFormula: PlanCalorieFormula,
    val phases: List<PlanPhase>,
    val weeks: List<PlanWeek>,
    val phaseDeficitKcalPerDay: Map<Int, Double>,
) {
    fun weekFor(date: LocalDate): PlanWeek? = weeks.firstOrNull { it.contains(date) }

    companion object {
        /** Lädt und parst assets/training_plan.json. Liefert null, wenn das Asset fehlt oder kaputt ist. */
        fun loadFromAssets(context: Context): TrainingPlan? = try {
            val json = context.assets.open("training_plan.json").bufferedReader().use { it.readText() }
            parse(json)
        } catch (e: Exception) {
            null
        }

        private fun parse(json: String): TrainingPlan {
            val root = JSONObject(json)
            val meta = root.getJSONObject("meta")
            val athleteJson = meta.getJSONObject("athlete")
            val raceJson = meta.getJSONObject("race")
            val formulaJson = root.getJSONObject("calorieFormula")

            val athlete = PlanAthlete(
                name = athleteJson.getString("name"),
                heightCm = athleteJson.getDouble("heightCm"),
                ageYears = athleteJson.getInt("ageYears"),
            )
            val race = PlanRace(
                name = raceJson.getString("name"),
                date = LocalDate.parse(raceJson.getString("date")),
                goalTime = raceJson.getString("goalTime"),
                goalPacePerKm = raceJson.getString("goalPacePerKm"),
            )
            val calorieFormula = PlanCalorieFormula(
                neatFactor = formulaJson.getDouble("neatFactor"),
                runningKcalPerKgPerKm = formulaJson.getDouble("runningKcalPerKgPerKm"),
                floorKcalPerDay = formulaJson.getInt("floorKcalPerDay"),
                roundToNearestKcal = formulaJson.getInt("roundToNearestKcal"),
            )

            val phasesJson = root.getJSONArray("phases")
            val phases = (0 until phasesJson.length()).map { i ->
                val p = phasesJson.getJSONObject(i)
                val range = p.getJSONArray("weekRange")
                PlanPhase(
                    id = p.getInt("id"),
                    name = p.getString("name"),
                    weekRange = range.getInt(0)..range.getInt(1),
                    focus = p.getString("focus"),
                )
            }

            val weeksJson = root.getJSONArray("weeks")
            val weeks = (0 until weeksJson.length()).map { i ->
                val w = weeksJson.getJSONObject(i)
                val sessionsJson = w.getJSONArray("sessions")
                val sessions = (0 until sessionsJson.length()).map { j ->
                    val s = sessionsJson.getJSONObject(j)
                    PlanSession(
                        type = s.getString("type"),
                        kind = s.getString("kind"),
                        day = s.optStringOrNull("day"),
                        isRest = s.getBoolean("isRest"),
                        detail = s.getString("detail"),
                    )
                }
                PlanWeek(
                    week = w.getInt("week"),
                    startDate = LocalDate.parse(w.getString("startDate")),
                    endDate = LocalDate.parse(w.getString("endDate")),
                    phaseId = w.getInt("phaseId"),
                    phaseName = w.getString("phaseName"),
                    volumeKm = w.getDouble("volumeKm"),
                    placeholderTargetKcalPerDay = w.getInt("targetKcalPerDay"),
                    placeholderTargetKcalPerWeek = w.getInt("targetKcalPerWeek"),
                    sessions = sessions,
                    milestone = w.optStringOrNull("milestone"),
                )
            }

            val deficitJson = root.getJSONObject("liveRecalculation").getJSONObject("phaseDeficitKcalPerDay")
            val deficits = deficitJson.keys().asSequence()
                .associate { key -> key.toInt() to deficitJson.getDouble(key) }

            return TrainingPlan(
                athlete = athlete,
                race = race,
                planStartDate = LocalDate.parse(meta.getString("planStartDate")),
                calorieFormula = calorieFormula,
                phases = phases,
                weeks = weeks,
                phaseDeficitKcalPerDay = deficits,
            )
        }
    }
}

data class LiveCalorieTarget(
    val rollingWeightKg: Double,
    val bmr: Double,
    val tdee: Double,
    val runKcal: Double,
    val deficit: Double,
    val targetKcalPerDay: Int,
    val targetKcalPerWeek: Int,
)

/**
 * Setzt liveRecalculation.algorithmPseudocode aus dem Trainingsplan-JSON um:
 * BMR (Mifflin-St Jeor) * NEAT-Faktor + geschätzter Laufverbrauch der Wochen-
 * Kilometer - festes Phasen-Defizit, auf volle 25 kcal gerundet, nie unter den
 * Floor-Wert. Das Defizit wird bewusst NICHT nachgezogen, wenn der Fortschritt
 * hinter dem Plan liegt — siehe liveRecalculation.rationale im JSON.
 */
fun TrainingPlan.calculateLiveTarget(week: PlanWeek, rollingWeightKg: Double): LiveCalorieTarget {
    val bmr = 10 * rollingWeightKg + 6.25 * athlete.heightCm - 5 * athlete.ageYears + 5
    val tdeeBase = bmr * calorieFormula.neatFactor
    val runKcal = (rollingWeightKg * week.volumeKm * calorieFormula.runningKcalPerKgPerKm) / 7.0
    val tdee = tdeeBase + runKcal
    val deficit = phaseDeficitKcalPerDay[week.phaseId] ?: 0.0
    val raw = tdee - deficit
    val step = calorieFormula.roundToNearestKcal
    val rounded = (Math.round(raw / step) * step).toInt()
    val targetPerDay = maxOf(calorieFormula.floorKcalPerDay, rounded)
    return LiveCalorieTarget(
        rollingWeightKg = rollingWeightKg,
        bmr = bmr,
        tdee = tdee,
        runKcal = runKcal,
        deficit = deficit,
        targetKcalPerDay = targetPerDay,
        targetKcalPerWeek = targetPerDay * 7,
    )
}
