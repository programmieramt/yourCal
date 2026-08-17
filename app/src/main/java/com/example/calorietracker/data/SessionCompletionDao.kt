package com.example.calorietracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCompleted(entry: SessionCompletionEntry)

    @Query("DELETE FROM session_completions WHERE weekNumber = :weekNumber AND sessionIndex = :sessionIndex")
    suspend fun setIncomplete(weekNumber: Int, sessionIndex: Int)

    @Query("SELECT * FROM session_completions")
    fun observeAll(): Flow<List<SessionCompletionEntry>>
}
