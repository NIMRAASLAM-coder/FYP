package com.fyp.nextshot.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fyp.nextshot.data.local.dao.AiTipDao
import com.fyp.nextshot.data.local.dao.SessionDao
import com.fyp.nextshot.data.local.models.AiTipEntity
import com.fyp.nextshot.data.local.models.SessionEntity

@Database(entities = [SessionEntity::class, AiTipEntity::class], version = 3, exportSchema = false)  // BUMPED: From 2 to 3 (added AiTipEntity)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun aiTipDao(): AiTipDao

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
                    .fallbackToDestructiveMigration()  // Auto-reset on version bump (wipes old schema)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}