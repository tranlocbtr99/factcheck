package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FactCheckEntity::class], version = 1, exportSchema = false)
abstract class FactCheckDatabase : RoomDatabase() {
    abstract fun factCheckDao(): FactCheckDao

    companion object {
        @Volatile
        private var instance: FactCheckDatabase? = null

        fun getInstance(context: Context): FactCheckDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FactCheckDatabase::class.java,
                    "ltm_factcheck.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
