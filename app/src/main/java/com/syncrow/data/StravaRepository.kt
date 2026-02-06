package com.syncrow.data

import android.content.Context
import android.util.Log
import com.syncrow.BuildConfig
import com.syncrow.data.api.StravaApi
import com.syncrow.data.db.MetricPoint
import com.syncrow.data.db.User
import com.syncrow.data.db.UserDao
import com.syncrow.data.db.Workout
import com.syncrow.data.db.WorkoutDao
import com.syncrow.util.TcxExporter
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class StravaRepository(
  private val context: Context,
  private val stravaApi: StravaApi,
  private val userDao: UserDao,
  private val workoutDao: WorkoutDao
) {

  private val clientId = BuildConfig.STRAVA_CLIENT_ID
  private val clientSecret = BuildConfig.STRAVA_CLIENT_SECRET

  suspend fun connect(code: String, user: User) {
    if (clientId.isBlank() || clientSecret.isBlank()) {
      Log.e("StravaRepository", "Strava credentials missing in BuildConfig")
      throw IllegalStateException("Strava credentials not configured")
    }

    try {
      val id =
        clientId.toIntOrNull() ?: throw IllegalStateException("Invalid Strava Client ID: $clientId")
      val response = stravaApi.getAccessToken(id, clientSecret, code)
      val updatedUser =
        user.copy(
          stravaToken = response.accessToken,
          stravaRefreshToken = response.refreshToken,
          stravaTokenExpiresAt = response.expiresAt
        )
      userDao.updateUser(updatedUser)
      Log.d("StravaRepository", "Successfully connected to Strava for user ${user.name}")
    } catch (e: Exception) {
      Log.e("StravaRepository", "Error connecting to Strava", e)
      throw e
    }
  }

  suspend fun getValidToken(user: User): String? {
    val expiresAt = user.stravaTokenExpiresAt ?: return null
    val refreshToken = user.stravaRefreshToken ?: return null

    // Refresh if expiring in less than 10 minutes
    if (System.currentTimeMillis() / 1000 > expiresAt - 600) {
      if (clientId.isBlank()) return null
      val id = clientId.toIntOrNull() ?: return null

      return try {
        val response = stravaApi.refreshAccessToken(id, clientSecret, refreshToken)
        val updatedUser =
          user.copy(
            stravaToken = response.accessToken,
            stravaRefreshToken = response.refreshToken,
            stravaTokenExpiresAt = response.expiresAt
          )
        userDao.updateUser(updatedUser)
        response.accessToken
      } catch (e: Exception) {
        Log.e("StravaRepository", "Error refreshing token", e)
        null
      }
    }
    return user.stravaToken
  }

  suspend fun uploadWorkout(workout: Workout, points: List<MetricPoint>, user: User): Boolean {
    val token = getValidToken(user) ?: return false

    val tcxString = TcxExporter(context).generateTcx(workout, points)
    if (tcxString.isBlank()) return false

    val file = File(context.cacheDir, "upload_${workout.id}.tcx")
    file.writeText(tcxString)

    val requestFile = file.asRequestBody("application/xml".toMediaTypeOrNull())
    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

    val name =
      "SyncRow: ${workout.totalDistanceMeters}m Row".toRequestBody("text/plain".toMediaTypeOrNull())
    val description = "Rowed with SyncRow App".toRequestBody("text/plain".toMediaTypeOrNull())
    val trainer = "0".toRequestBody("text/plain".toMediaTypeOrNull()) // 0 for virtual activities
    val commute = "0".toRequestBody("text/plain".toMediaTypeOrNull())
    val dataType = "tcx".toRequestBody("text/plain".toMediaTypeOrNull())
    val externalId = "syncrow_${workout.id}".toRequestBody("text/plain".toMediaTypeOrNull())
    val activityType = "virtualrow".toRequestBody("text/plain".toMediaTypeOrNull())
    val sportType = "VirtualRow".toRequestBody("text/plain".toMediaTypeOrNull())

    return try {
      val response =
        stravaApi.uploadActivity(
          "Bearer $token",
          body,
          name,
          description,
          trainer,
          commute,
          dataType,
          externalId,
          activityType,
          sportType
        )
      Log.d("StravaRepository", "Upload started: ${response.id}")
      workoutDao.updateWorkout(workout.copy(stravaActivityId = response.id))
      true
    } catch (e: Exception) {
      if (e is HttpException && e.code() == 409) {
        Log.d("StravaRepository", "Workout ${workout.id} already exists on Strava (409 Conflict)")
        // Mark it as synced if it wasn't already
        if (workout.stravaActivityId == null) {
          workoutDao.updateWorkout(workout.copy(stravaActivityId = -1L))
        }
        return true
      }
      Log.e("StravaRepository", "Error uploading workout", e)
      false
    } finally {
      if (file.exists()) file.delete()
    }
  }
}
