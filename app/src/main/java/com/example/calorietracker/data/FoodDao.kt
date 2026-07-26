package com.example.calorietracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("SELECT * FROM food_entries WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSince(since: Long): Flow<List<FoodEntry>>
}
