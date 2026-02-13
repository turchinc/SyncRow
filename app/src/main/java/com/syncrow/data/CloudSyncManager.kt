package com.syncrow.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.syncrow.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class CloudSyncManager(
  private val userDao: UserDao,
  private val workoutDao: WorkoutDao,
  private val trainingDao: TrainingDao,
  private val splitDao: SplitDao
) {
  private val auth = FirebaseAuth.getInstance()
  private val db = FirebaseFirestore.getInstance()

  // Increment this whenever the data structure in Firestore changes significantly
  private val SYNC_VERSION = 1

  suspend fun updateSyncStatus(user: User) {
    if (user.cloudSyncEnabled) {
      ensureAuthenticated(user)
      syncProfile(user)
      syncAllData(user)
    }
  }

  private suspend fun ensureAuthenticated(user: User) {
    val currentUser = auth.currentUser
    if (currentUser == null) {
      try {
        val result = auth.signInAnonymously().await()
        val firebaseUid = result.user?.uid
        if (firebaseUid != null) {
          userDao.updateUser(user.copy(firebaseUid = firebaseUid))
        }
      } catch (e: Exception) {
        Log.e("CloudSyncManager", "Anonymous auth failed", e)
      }
    } else if (user.firebaseUid != currentUser.uid) {
      userDao.updateUser(user.copy(firebaseUid = currentUser.uid))
    }
  }

  private suspend fun syncProfile(user: User) {
    val uid = auth.currentUser?.uid ?: return
    val profileData =
      hashMapOf(
        "syncVersion" to SYNC_VERSION,
        "name" to user.name,
        "weightKg" to user.weightKg,
        "heightCm" to user.heightCm,
        "age" to user.age,
        "gender" to user.gender,
        "languageCode" to user.languageCode,
        "themeMode" to user.themeMode,
        "autoUploadToStrava" to user.autoUploadToStrava,
        "lastUpdated" to user.lastUpdated
      )

    try {
      db.collection("users").document(uid).set(profileData, SetOptions.merge()).await()
      Log.d("CloudSyncManager", "Profile synced for $uid")
    } catch (e: Exception) {
      Log.e("CloudSyncManager", "Firestore sync failed", e)
    }
  }

  private suspend fun syncAllData(user: User) {
    val uid = auth.currentUser?.uid ?: return

    // Sync Workouts (with Splits)
    val workouts = workoutDao.getWorkoutsForUser(user.id).first()
    workouts.forEach { workout ->
      val splits = splitDao.getSplitsForWorkoutSync(workout.id)
      val workoutData =
        hashMapOf(
          "syncVersion" to SYNC_VERSION,
          "globalId" to workout.globalId,
          "startTime" to workout.startTime,
          "endTime" to workout.endTime,
          "totalDistanceMeters" to workout.totalDistanceMeters,
          "totalSeconds" to workout.totalSeconds,
          "avgPower" to workout.avgPower,
          "avgHeartRate" to workout.avgHeartRate,
          "activityType" to workout.activityType,
          "notes" to workout.notes,
          "splits" to
            splits.map { split ->
              mapOf(
                "splitIndex" to split.splitIndex,
                "distanceMeters" to split.distanceMeters,
                "durationSeconds" to split.durationSeconds,
                "avgPace" to split.avgPace,
                "avgPower" to split.avgPower,
                "avgHeartRate" to split.avgHeartRate,
                "avgStrokeRate" to split.avgStrokeRate
              )
            }
        )
      db
        .collection("users")
        .document(uid)
        .collection("workouts")
        .document(workout.globalId)
        .set(workoutData, SetOptions.merge())
    }

    // Sync Training Plans (Full Hierarchy)
    val plansWithDetails = trainingDao.getAllPlansWithDetails().first()
    plansWithDetails.forEach { detailedPlan ->
      val plan = detailedPlan.plan
      val planData =
        hashMapOf(
          "syncVersion" to SYNC_VERSION,
          "globalId" to plan.globalId,
          "name" to plan.name,
          "description" to plan.description,
          "difficulty" to plan.difficulty,
          "intensity" to plan.intensity,
          "isFavorite" to plan.isFavorite,
          "activityType" to plan.activityType,
          "createdAt" to plan.createdAt,
          "blocks" to
            detailedPlan.blocks.map { blockWithSegments ->
              val block = blockWithSegments.block
              mapOf(
                "name" to block.name,
                "orderIndex" to block.orderIndex,
                "repeatCount" to block.repeatCount,
                "segments" to
                  blockWithSegments.segments.map { seg ->
                    mapOf(
                      "orderIndex" to seg.orderIndex,
                      "segmentType" to seg.segmentType,
                      "durationType" to seg.durationType,
                      "durationValue" to seg.durationValue,
                      "targetSpm" to seg.targetSpm,
                      "targetWatts" to seg.targetWatts,
                      "targetPace" to seg.targetPace,
                      "targetHr" to seg.targetHr
                    )
                  }
              )
            }
        )
      db
        .collection("users")
        .document(uid)
        .collection("training_plans")
        .document(plan.globalId)
        .set(planData, SetOptions.merge())
    }

    Log.d("CloudSyncManager", "Full data sync triggered for $uid")
  }

  /**
   * Pulls data from Firestore and restores it locally. If [targetUser] is provided, it updates that
   * user.
   */
  suspend fun pullAndRestoreData(targetUser: User): Boolean {
    val uid = auth.currentUser?.uid ?: return false
    try {
      // 1. Restore Profile
      val profileDoc = db.collection("users").document(uid).get().await()
      if (profileDoc.exists()) {
        val cloudLastUpdated = profileDoc.getLong("lastUpdated") ?: 0L

        // Update local user if cloud is newer OR if the local user doesn't have a Firebase UID yet
        // (fresh setup)
        if (cloudLastUpdated > targetUser.lastUpdated || targetUser.firebaseUid == null) {
          val updatedUser =
            targetUser.copy(
              name = profileDoc.getString("name") ?: targetUser.name,
              weightKg = profileDoc.getDouble("weightKg"),
              heightCm = profileDoc.getLong("heightCm")?.toInt(),
              age = profileDoc.getLong("age")?.toInt(),
              gender = profileDoc.getString("gender"),
              languageCode = profileDoc.getString("languageCode") ?: targetUser.languageCode,
              themeMode = profileDoc.getString("themeMode") ?: targetUser.themeMode,
              autoUploadToStrava = profileDoc.getBoolean("autoUploadToStrava") ?: false,
              lastUpdated = cloudLastUpdated,
              firebaseUid = uid
            )
          userDao.updateUser(updatedUser)
        }
      } else {
        // No cloud profile exists for this UID. Just link the local user to this UID.
        userDao.updateUser(targetUser.copy(firebaseUid = uid))
        return true
      }

      // 2. Restore Workouts
      val workoutsSnap = db.collection("users").document(uid).collection("workouts").get().await()
      for (doc in workoutsSnap.documents) {
        val globalId = doc.getString("globalId") ?: continue
        if (workoutDao.getWorkoutByGlobalId(globalId) == null) {
          val workoutId =
            workoutDao.insertWorkout(
              Workout(
                userId = targetUser.id,
                globalId = globalId,
                startTime = doc.getLong("startTime") ?: 0L,
                endTime = doc.getLong("endTime"),
                totalDistanceMeters = doc.getLong("totalDistanceMeters")?.toInt() ?: 0,
                totalSeconds = doc.getLong("totalSeconds")?.toInt() ?: 0,
                avgPower = doc.getLong("avgPower")?.toInt() ?: 0,
                avgHeartRate = doc.getLong("avgHeartRate")?.toInt() ?: 0,
                notes = doc.getString("notes"),
                activityType = doc.getString("activityType") ?: ActivityType.ROWING.name
              )
            )

          val splitsData = doc.get("splits") as? List<Map<String, Any>>
          splitsData?.forEach { s ->
            splitDao.insertSplit(
              WorkoutSplit(
                workoutId = workoutId,
                splitIndex = (s["splitIndex"] as? Long)?.toInt() ?: 0,
                startTime = 0L,
                endTime = 0L,
                distanceMeters = (s["distanceMeters"] as? Long)?.toInt() ?: 0,
                durationSeconds = (s["durationSeconds"] as? Long)?.toInt() ?: 0,
                avgPace = (s["avgPace"] as? Long)?.toInt() ?: 0,
                avgPower = (s["avgPower"] as? Long)?.toInt() ?: 0,
                avgHeartRate = (s["avgHeartRate"] as? Long)?.toInt() ?: 0,
                avgStrokeRate = (s["avgStrokeRate"] as? Long)?.toInt() ?: 0
              )
            )
          }
        }
      }

      // 3. Restore Training Plans
      val plansSnap =
        db.collection("users").document(uid).collection("training_plans").get().await()
      for (doc in plansSnap.documents) {
        val globalId = doc.getString("globalId") ?: continue
        if (trainingDao.getPlanByGlobalId(globalId) == null) {
          val planId =
            trainingDao.insertTrainingPlan(
              TrainingPlan(
                globalId = globalId,
                name = doc.getString("name") ?: "Restored Plan",
                description = doc.getString("description") ?: "",
                difficulty = doc.getString("difficulty") ?: "Beginner",
                intensity = doc.getString("intensity") ?: "Medium",
                isFavorite = doc.getBoolean("isFavorite") ?: false,
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                activityType = doc.getString("activityType") ?: ActivityType.ROWING.name
              )
            )

          val blocksData = doc.get("blocks") as? List<Map<String, Any>>
          blocksData?.forEach { b ->
            val blockId =
              trainingDao.insertBlock(
                TrainingBlock(
                  planId = planId,
                  orderIndex = (b["orderIndex"] as? Long)?.toInt() ?: 0,
                  name = b["name"] as? String ?: "Block",
                  repeatCount = (b["repeatCount"] as? Long)?.toInt() ?: 1
                )
              )

            val segmentsData = b["segments"] as? List<Map<String, Any>>
            segmentsData?.forEach { s ->
              trainingDao.insertSegment(
                TrainingSegment(
                  blockId = blockId,
                  orderIndex = (s["orderIndex"] as? Long)?.toInt() ?: 0,
                  segmentType = s["segmentType"] as? String ?: SegmentType.ACTIVE.name,
                  durationType = s["durationType"] as? String ?: DurationType.TIME.name,
                  durationValue = (s["durationValue"] as? Long)?.toInt() ?: 0,
                  targetSpm = (s["targetSpm"] as? Long)?.toInt(),
                  targetWatts = (s["targetWatts"] as? Long)?.toInt(),
                  targetPace = (s["targetPace"] as? Long)?.toInt(),
                  targetHr = (s["targetHr"] as? Long)?.toInt()
                )
              )
            }
          }
        }
      }
      return true
    } catch (e: Exception) {
      Log.e("CloudSyncManager", "Restore failed", e)
      return false
    }
  }
}
