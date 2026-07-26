package com.example.calorietracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Insert
    suspend fun insert(entry: WeightEntry): Long

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WeightEntry>>
}
