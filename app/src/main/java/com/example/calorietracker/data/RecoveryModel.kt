package com.example.calorietracker.data

import java.time.LocalDate

enum class RecoveryStatus { GREEN, YELLOW, RED }

/** Fitness (CTL)/Fatigue (ATL)/Form-Stand an einem Tag, plus Ampel-Einordnung für die UI. */
data class RecoveryState(
    val date: LocalDate,
    val ctl: Double,
    val atl: Double,
) {
    val form: Double get() = ctl - atl

    val status: RecoveryStatus
        get() = when {
            form < -30.0 -> RecoveryStatus.RED
            form > 25.0 -> RecoveryStatus.YELLOW
            else -> RecoveryStatus.GREEN
        }

    val hint: String
        get() = when (status) {
            RecoveryStatus.RED -> "Hohe Belastung — heute eher locker oder Pause."
            RecoveryStatus.YELLOW -> "Gut erholt — ggf. Platz für einen intensiveren Reiz."
            RecoveryStatus.GREEN -> "Normale Trainingslast."
        }
}

/**
 * Vereinfachtes Banister-Impulse-Response-Modell für Fitness/Fatigue/Form.
 *
 * ATL (kurzfristige Ermüdung, Zeitkonstante 7 Tage) und CTL (langfristige Fitness,
 * Zeitkonstante 42 Tage) sind exponentiell gleitende Mittelwerte des täglichen
 * Trainingsreizes (Load). Form = CTL - ATL zeigt, ob gerade mehr akute Ermüdung als
 * Grundlagenfitness da ist (negativ) oder ob Erholungsreserven vorhanden sind (positiv).
 *
 * Rechnet Tag für Tag fort — auch mit Load=0 an Tagen ohne Training — statt direkt
 * von "letztem bekannten Tag" auf "heute" zu springen, damit das exponentielle
 * Abklingen/Aufbauen über mehrtägige Lücken hinweg korrekt bleibt.
 */
object RecoveryModel {
    private const val ATL_TIME_CONSTANT = 7.0
    private const val CTL_TIME_CONSTANT = 42.0

    private fun step(previous: RecoveryState, nextDate: LocalDate, load: Double): RecoveryState {
        val atl = previous.atl + (load - previous.atl) / ATL_TIME_CONSTANT
        val ctl = previous.ctl + (load - previous.ctl) / CTL_TIME_CONSTANT
        return RecoveryState(date = nextDate, ctl = ctl, atl = atl)
    }

    /**
     * Schreibt [from] Tag für Tag bis [to] (inklusive) fort. [loadByDate] enthält den
     * Trainingsreiz je Kalendertag; fehlt ein Tag darin, wird Load=0 angenommen
     * (Ruhetag). Ist [to] nicht nach [from].date, wird [from] unverändert zurückgegeben.
     */
    fun advance(from: RecoveryState, to: LocalDate, loadByDate: Map<LocalDate, Double>): RecoveryState {
        var state = from
        var day = from.date.plusDays(1)
        while (!day.isAfter(to)) {
            state = step(state, day, loadByDate[day] ?: 0.0)
            day = day.plusDays(1)
        }
        return state
    }

    /**
     * Trainingsreiz aus Dauer (Minuten) und Ø-Herzfrequenz, vereinfacht als
     * Dauer × (Ø-HF / HFmax). Ohne Herzfrequenzdaten (z.B. Krafttraining ohne
     * Brustgurt) trägt die Einheit aktuell nicht zum Load bei.
     */
    fun loadFrom(durationMin: Double, avgHr: Double?, hrMax: Double): Double {
        if (avgHr == null || avgHr <= 0.0 || hrMax <= 0.0) return 0.0
        return durationMin * (avgHr / hrMax)
    }
}
