package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FactCheckEntity::class], version = 2, exportSchema = false)
abstract class FactCheckDatabase : RoomDatabase() {
    abstract fun factCheckDao(): FactCheckDao

    companion object {
        @Volatile private var instance: FactCheckDatabase? = null

        fun getInstance(context: Context): FactCheckDatabase {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                FactCheckDatabase::class.java,
                                "ltm_factcheck.db",
                            )
                            .addMigrations(
                                object : androidx.room.migration.Migration(1, 2) {
                                    override fun migrate(
                                        db: androidx.sqlite.db.SupportSQLiteDatabase
                                    ) {
                                        db.execSQL(
                                            "ALTER TABLE fact_check_records ADD COLUMN searchEntryPoint TEXT NOT NULL DEFAULT ''"
                                        )
                                    }
                                }
                            )
                            .build()
                            .also { instance = it }
                }
        }
    }
}
