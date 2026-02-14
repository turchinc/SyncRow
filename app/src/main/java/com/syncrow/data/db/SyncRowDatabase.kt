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
  version = 15,
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

    val MIGRATION_14_15 =
      object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
          // 1. Add lastUpdated to training_plans
          // SQLite does not allow non-constant defaults in ALTER TABLE ADD COLUMN
          database.execSQL(
            "ALTER TABLE training_plans ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0"
          )
          database.execSQL("UPDATE training_plans SET lastUpdated = (strftime('%s','now') * 1000)")

          // 2. Create unique indices for globalId to prevent duplication during sync/import
          database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_workouts_globalId ON workouts(globalId)"
          )
          database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_training_plans_globalId ON training_plans(globalId)"
          )
          database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_personal_bests_globalId ON personal_bests(globalId)"
          )
        }
      }

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
          database.execSQL(
            "ALTER TABLE training_plans ADD COLUMN globalId TEXT NOT NULL DEFAULT ''"
          )

          val cursor = database.query("SELECT id FROM training_plans")
          while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val uuid = UUID.randomUUID().toString()
            database.execSQL("UPDATE training_plans SET globalId = '$uuid' WHERE id = $id")
          }
          cursor.close()
        }
      }

    val MIGRATION_12_13 =
      object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
          database.execSQL(
            "ALTER TABLE users ADD COLUMN cloudSyncEnabled INTEGER NOT NULL DEFAULT 0"
          )
          database.execSQL("ALTER TABLE users ADD COLUMN firebaseUid TEXT")
        }
      }

    val MIGRATION_13_14 =
      object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
          // SQLite does not allow non-constant defaults in ALTER TABLE ADD COLUMN
          database.execSQL("ALTER TABLE users ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
          database.execSQL("UPDATE users SET lastUpdated = (strftime('%s','now') * 1000)")

          database.execSQL("ALTER TABLE workouts ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
          val cursorWorkouts = database.query("SELECT id FROM workouts")
          while (cursorWorkouts.moveToNext()) {
            val id = cursorWorkouts.getLong(0)
            val uuid = UUID.randomUUID().toString()
            database.execSQL("UPDATE workouts SET globalId = '$uuid' WHERE id = $id")
          }
          cursorWorkouts.close()

          database.execSQL(
            "ALTER TABLE personal_bests ADD COLUMN globalId TEXT NOT NULL DEFAULT ''"
          )
          val cursorPb = database.query("SELECT id FROM personal_bests")
          while (cursorPb.moveToNext()) {
            val id = cursorPb.getLong(0)
            val uuid = UUID.randomUUID().toString()
            database.execSQL("UPDATE personal_bests SET globalId = '$uuid' WHERE id = $id")
          }
          cursorPb.close()
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
              .addMigrations(
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15
              )
              .build()
          INSTANCE = instance
          instance
        }
    }
  }
}
