package com.example.calorietracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodEntry::class,
        FavoriteEntry::class,
        WeightEntry::class,
        ExerciseEntry::class,
        SessionCompletionEntry::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun weightDao(): WeightDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionCompletionDao(): SessionCompletionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calorie_tracker.db",
                )
                    // Keine Migrationen bisher nötig — App noch ohne Nutzerbasis.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
