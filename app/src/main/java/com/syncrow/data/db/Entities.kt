package com.syncrow.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val weightKg: Double? = null,
    val heightCm: Int? = null,
    val age: Int? = null,
    val gender: String? = null,
    val stravaToken: String? = null,
    val languageCode: String = "en"
)

@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val startTime: Long, // Epoch millis
    var endTime: Long? = null,
    var totalDistanceMeters: Int = 0,
    var totalSeconds: Int = 0,
    var avgPower: Int = 0,
    var avgHeartRate: Int = 0,
    val notes: String? = null
)

@Entity(
    tableName = "metric_points",
    foreignKeys = [
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
    tableName = "personal_bests",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class PersonalBest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val distanceCategory: String, // e.g., "500m", "2000m", "5000m", "30min"
    val bestValue: Double, // Time in seconds or distance in meters
    val date: Long
)
