package com.syncrow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [User::class, Workout::class, MetricPoint::class, PersonalBest::class],
  version = 3,
  exportSchema = false
)
abstract class SyncRowDatabase : RoomDatabase() {
  abstract fun userDao(): UserDao

  abstract fun workoutDao(): WorkoutDao

  abstract fun metricPointDao(): MetricPointDao

  companion object {
    @Volatile private var INSTANCE: SyncRowDatabase? = null

    fun getDatabase(context: Context): SyncRowDatabase {
      return INSTANCE
        ?: synchronized(this) {
          val instance =
            Room.databaseBuilder(
                context.applicationContext,
                SyncRowDatabase::class.java,
                "syncrow_database"
              )
              .fallbackToDestructiveMigration()
              .build()
          INSTANCE = instance
          instance
        }
    }
  }
}
