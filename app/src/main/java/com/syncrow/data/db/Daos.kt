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

data class TrainingBlockWithSegments(
  @Embedded val block: TrainingBlock,
  @Relation(parentColumn = "id", entityColumn = "blockId") val segments: List<TrainingSegment>
)

data class TrainingPlanWithBlocks(
  @Embedded val plan: TrainingPlan,
  @Relation(entity = TrainingBlock::class, parentColumn = "id", entityColumn = "planId")
  val blocks: List<TrainingBlockWithSegments>
)

@Dao
interface TrainingDao {
  @Query("SELECT * FROM training_plans ORDER BY createdAt DESC")
  fun getAllTrainingPlans(): Flow<List<TrainingPlan>>

  @Transaction
  @Query("SELECT * FROM training_plans ORDER BY createdAt DESC")
  fun getAllPlansWithDetails(): Flow<List<TrainingPlanWithBlocks>>

  @Query("SELECT * FROM training_plans WHERE isFavorite = 1 ORDER BY createdAt DESC")
  fun getFavoriteTrainingPlans(): Flow<List<TrainingPlan>>

  @Insert suspend fun insertTrainingPlan(plan: TrainingPlan): Long

  @Update suspend fun updateTrainingPlan(plan: TrainingPlan)

  @Delete suspend fun deleteTrainingPlan(plan: TrainingPlan)

  // Blocks
  @Insert suspend fun insertBlock(block: TrainingBlock): Long

  @Query("DELETE FROM training_blocks WHERE planId = :planId")
  suspend fun deleteBlocksForPlan(planId: Long)

  @Query("SELECT * FROM training_blocks WHERE planId = :planId ORDER BY orderIndex ASC")
  suspend fun getBlocksForPlanSync(planId: Long): List<TrainingBlock>

  // Segments
  @Insert suspend fun insertSegment(segment: TrainingSegment)

  @Insert suspend fun insertSegments(segments: List<TrainingSegment>)

  @Query("DELETE FROM training_segments WHERE blockId = :blockId")
  suspend fun deleteSegmentsForBlock(blockId: Long)

  @Query("SELECT * FROM training_segments WHERE blockId = :blockId ORDER BY orderIndex ASC")
  suspend fun getSegmentsForBlockSync(blockId: Long): List<TrainingSegment>
}
