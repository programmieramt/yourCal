package com.example.calorietracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert
    suspend fun insert(favorite: FavoriteEntry): Long

    @Delete
    suspend fun delete(favorite: FavoriteEntry)

    @Query("SELECT * FROM favorites ORDER BY id DESC")
    fun observeAll(): Flow<List<FavoriteEntry>>
}
