package com.example.calorietracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val weightKg: Double,
    /** Optional, meist von einer BIA-Waage — nur als geglätteter Trend interpretierbar, siehe HistoryScreen. */
    val bodyFatPercent: Double? = null,
    /** Optional, direkt von der Waage — nicht dasselbe wie das berechnete leanMassKg, siehe HistoryScreen. */
    val muscleMassKg: Double? = null,
)
