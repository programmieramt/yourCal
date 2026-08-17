package com.example.calorietracker.data

import androidx.room.Entity

/** Abhak-Status einer Trainingsplan-Session. Zeilenexistenz = erledigt; Zeile löschen = wieder offen. */
@Entity(tableName = "session_completions", primaryKeys = ["weekNumber", "sessionIndex"])
data class SessionCompletionEntry(
    val weekNumber: Int,
    val sessionIndex: Int,
    val completedAt: Long,
)
