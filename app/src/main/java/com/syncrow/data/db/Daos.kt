package com.syncrow.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
  @Query("SELECT * FROM users") fun getAllUsers(): Flow<List<User>>

  @Query("SELECT * FROM users WHERE id = :id") suspend fun getUserById(id: Long): User?

  @Insert suspend fun insertUser(user: User): Long

  @Update suspend fun updateUser(user: User)

  @Delete suspend fun deleteUser(user: User)
}

@Dao
interface WorkoutDao {
  @Query("SELECT * FROM workouts WHERE userId = :userId ORDER BY startTime DESC")
  fun getWorkoutsForUser(userId: Long): Flow<List<Workout>>

  @Insert suspend fun insertWorkout(workout: Workout): Long

  @Update suspend fun updateWorkout(workout: Workout)

  @Delete suspend fun deleteWorkout(workout: Workout)

  @Query("SELECT * FROM workouts WHERE id = :workoutId")
  suspend fun getWorkoutById(workoutId: Long): Workout?
}

@Dao
interface MetricPointDao {
  @Insert suspend fun insertMetricPoint(point: MetricPoint)

  @Query("SELECT * FROM metric_points WHERE workoutId = :workoutId ORDER BY timestamp ASC")
  fun getPointsForWorkout(workoutId: Long): Flow<List<MetricPoint>>

  // Helper to calculate averages for a split
  @Query(
    "SELECT * FROM metric_points WHERE workoutId = :workoutId AND timestamp >= :startTime AND timestamp <= :endTime"
  )
  suspend fun getPointsInRange(workoutId: Long, startTime: Long, endTime: Long): List<MetricPoint>
}

@Dao
interface SplitDao {
  @Insert suspend fun insertSplit(split: WorkoutSplit)

  @Query("SELECT * FROM workout_splits WHERE workoutId = :workoutId ORDER BY splitIndex ASC")
  fun getSplitsForWorkout(workoutId: Long): Flow<List<WorkoutSplit>>

  @Query("SELECT * FROM workout_splits WHERE workoutId = :workoutId ORDER BY splitIndex ASC")
  suspend fun getSplitsForWorkoutSync(workoutId: Long): List<WorkoutSplit>
}
