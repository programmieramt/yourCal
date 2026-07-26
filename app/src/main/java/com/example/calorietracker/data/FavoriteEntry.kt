package com.example.calorietracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
