package com.syncrow.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class User(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val weightKg: Double? = null,
  val heightCm: Int? = null,
  val age: Int? = null,
  val gender: String? = null,
  val stravaToken: String? = null,
  val stravaRefreshToken: String? = null,
  val stravaTokenExpiresAt: Long? = null,
  val autoUploadToStrava: Boolean = false,
  val languageCode: String = "en",
  val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
  val cloudSyncEnabled: Boolean = false,
  val firebaseUid: String? = null,
  val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "workouts",
  foreignKeys =
    [
      ForeignKey(
        entity = User::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
      )
    ],
  indices = [Index("userId"), Index(value = ["globalId"], unique = true)]
)
data class Workout(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val userId: Long,
  val globalId: String = UUID.randomUUID().toString(),
  val startTime: Long, // Epoch millis
  var endTime: Long? = null,
  var totalDistanceMeters: Int = 0,
  var totalSeconds: Int = 0,
  var avgPower: Int = 0,
  var avgHeartRate: Int = 0,
  val notes: String? = null,
  val stravaActivityId: Long? = null,
  val activityType: String = ActivityType.ROWING.name
)

@Entity(
  tableName = "metric_points",
  foreignKeys =
    [
      ForeignKey(
        entity = Workout::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
      )
    ],
  indices = [Index("workoutId")]
)
data class MetricPoint(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workoutId: Long,
  val timestamp: Long, // Epoch millis
  val power: Int,
  val pace: Int,
  val strokeRate: Int,
  val distance: Int,
  val heartRate: Int
)

@Entity(
  tableName = "workout_splits",
  foreignKeys =
    [
      ForeignKey(
        entity = Workout::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
      )
    ],
  indices = [Index("workoutId")]
)
data class WorkoutSplit(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workoutId: Long,
  val splitIndex: Int,
  val startTime: Long, // Epoch millis
  val endTime: Long,
  val distanceMeters: Int,
  val durationSeconds: Int,
  val avgPace: Int,
  val avgPower: Int,
  val avgHeartRate: Int,
  val avgStrokeRate: Int
)

@Entity(
  tableName = "personal_bests",
  foreignKeys =
    [
      ForeignKey(
        entity = User::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
      )
    ],
  indices = [Index("userId"), Index(value = ["globalId"], unique = true)]
)
data class PersonalBest(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val userId: Long,
  val globalId: String = UUID.randomUUID().toString(),
  val distanceCategory: String, // e.g., "500m", "2000m", "5000m", "30min"
  val bestValue: Double, // Time in seconds or distance in meters
  val date: Long
)

@Entity(tableName = "training_plans", indices = [Index(value = ["globalId"], unique = true)])
data class TrainingPlan(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @ColumnInfo(defaultValue = "")
  val globalId: String = UUID.randomUUID().toString(), // GUID for sync/dedup
  val name: String,
  val description: String,
  val difficulty: String, // Beginner, Intermediate, Advanced
  val intensity: String, // Easy, Medium, Hard
  val isFavorite: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
  val activityType: String = ActivityType.ROWING.name,
  val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
  tableName = "training_blocks",
  foreignKeys =
    [
      ForeignKey(
        entity = TrainingPlan::class,
        parentColumns = ["id"],
        childColumns = ["planId"],
        onDelete = ForeignKey.CASCADE
      )
    ],
  indices = [Index("planId")]
)
data class TrainingBlock(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val planId: Long,
  val orderIndex: Int,
  val name: String, // e.g. "Warm-up", "Interval Set"
  val repeatCount: Int = 1 // Defines "Rounds"
)

enum class SegmentType {
  ACTIVE,
  RECOVERY, // Replaces "Rest"
  WARMUP,
  COOLDOWN
}

enum class DurationType {
  TIME,
  DISTANCE
}

enum class ActivityType {
  ROWING,
  CYCLING,
  RUNNING,
  ELLIPTICAL
}

@Entity(
  tableName = "training_segments",
  foreignKeys =
    [
      ForeignKey(
        entity = TrainingBlock::class,
        parentColumns = ["id"],
        childColumns = ["blockId"],
        onDelete = ForeignKey.CASCADE
      )
    ],
  indices = [Index("blockId")]
)
data class TrainingSegment(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val blockId: Long,
  val orderIndex: Int,
  val segmentType: String, // Store enum name: ACTIVE, RECOVERY
  val durationType: String, // Store enum name: TIME, DISTANCE
  val durationValue: Int, // Seconds or Meters

  // Targets (multiple allowed)
  val targetSpm: Int? = null,
  val targetWatts: Int? = null,
  val targetPace: Int? = null, // Seconds per 500m
  val targetHr: Int? = null
)
