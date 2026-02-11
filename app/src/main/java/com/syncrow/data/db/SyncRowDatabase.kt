package com.syncrow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
  entities =
    [
      User::class,
      Workout::class,
      MetricPoint::class,
      PersonalBest::class,
      WorkoutSplit::class,
      TrainingPlan::class,
      TrainingBlock::class,
      TrainingSegment::class
    ],
  version = 12,
  exportSchema = false
)
abstract class SyncRowDatabase : RoomDatabase() {
  abstract fun userDao(): UserDao

  abstract fun workoutDao(): WorkoutDao

  abstract fun metricPointDao(): MetricPointDao

  abstract fun splitDao(): SplitDao

  abstract fun trainingDao(): TrainingDao

  companion object {
    @Volatile private var INSTANCE: SyncRowDatabase? = null

    val MIGRATION_9_10 =
      object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
          // Add activityType column to workouts, default to ROWING
          database.execSQL(
            "ALTER TABLE workouts ADD COLUMN activityType TEXT NOT NULL DEFAULT 'ROWING'"
          )
          // Add activityType column to training_plans, default to ROWING
          database.execSQL(
            "ALTER TABLE training_plans ADD COLUMN activityType TEXT NOT NULL DEFAULT 'ROWING'"
          )
        }
      }

    val MIGRATION_10_11 =
      object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
          // 1. Add 'globalId' column as TEXT NOT NULL DEFAULT ''.
          // We use DEFAULT '' to satisfy NOT NULL constraint on existing rows and match Room schema
          // expectation.
          database.execSQL(
            "ALTER TABLE training_plans ADD COLUMN globalId TEXT NOT NULL DEFAULT ''"
          )

          // 2. Populate existing rows with random UUIDs
          val cursor = database.query("SELECT id FROM training_plans")
          while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val uuid = UUID.randomUUID().toString()
            database.execSQL("UPDATE training_plans SET globalId = '$uuid' WHERE id = $id")
          }
          cursor.close()
        }
      }

    fun getDatabase(context: Context): SyncRowDatabase {
      return INSTANCE
        ?: synchronized(this) {
          val instance =
            Room.databaseBuilder(
                context.applicationContext,
                SyncRowDatabase::class.java,
                "syncrow_database"
              )
              .addMigrations(MIGRATION_9_10, MIGRATION_10_11)
              .fallbackToDestructiveMigration()
              .build()
          INSTANCE = instance
          instance
        }
    }
  }
}
