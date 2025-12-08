package com.fyp.nextshot.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fyp.nextshot.data.local.dao.SessionDao
import com.fyp.nextshot.data.local.models.SessionEntity

@Database(entities = [SessionEntity::class], version = 2, exportSchema = false)  // BUMPED: From 1 to 2
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cricket_app_database"
                )
                    .fallbackToDestructiveMigration()  // ADDED: Auto-reset on version bump (wipes old schema)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}