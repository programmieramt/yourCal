package com.example.calorietracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert
    suspend fun insert(entry: ExerciseEntry): Long

    @Delete
    suspend fun delete(entry: ExerciseEntry)

    @Query("SELECT * FROM exercise_entries WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSince(since: Long): Flow<List<ExerciseEntry>>

    @Query("SELECT * FROM exercise_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ExerciseEntry>>
}
