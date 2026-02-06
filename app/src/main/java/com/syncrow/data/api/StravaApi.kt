package com.syncrow.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface StravaApi {

  @POST("https://www.strava.com/oauth/token")
  suspend fun getAccessToken(
    @Query("client_id") clientId: Int,
    @Query("client_secret") clientSecret: String,
    @Query("code") code: String,
    @Query("grant_type") grantType: String = "authorization_code"
  ): TokenResponse

  @POST("https://www.strava.com/oauth/token")
  suspend fun refreshAccessToken(
    @Query("client_id") clientId: Int,
    @Query("client_secret") clientSecret: String,
    @Query("refresh_token") refreshToken: String,
    @Query("grant_type") grantType: String = "refresh_token"
  ): TokenResponse

  @Multipart
  @POST("https://www.strava.com/api/v3/uploads")
  suspend fun uploadActivity(
    @Header("Authorization") accessToken: String,
    @Part file: MultipartBody.Part,
    @Part("name") name: RequestBody,
    @Part("description") description: RequestBody,
    @Part("trainer") trainer: RequestBody,
    @Part("commute") commute: RequestBody,
    @Part("data_type") dataType: RequestBody,
    @Part("external_id") externalId: RequestBody,
    @Part("activity_type") activityType: RequestBody?,
    @Part("sport_type") sportType: RequestBody?
  ): UploadResponse

  @GET("https://www.strava.com/api/v3/uploads/{id}")
  suspend fun getUploadStatus(
    @Header("Authorization") accessToken: String,
    @Path("id") uploadId: Long
  ): UploadStatusResponse
}

data class TokenResponse(
  @SerializedName("access_token") val accessToken: String,
  @SerializedName("refresh_token") val refreshToken: String,
  @SerializedName("expires_at") val expiresAt: Long
)

data class UploadResponse(
  val id: Long,
  @SerializedName("external_id") val externalId: String?,
  val error: String?,
  val status: String,
  @SerializedName("activity_id") val activityId: Long?
)

data class UploadStatusResponse(
  val id: Long,
  @SerializedName("external_id") val externalId: String?,
  val error: String?,
  val status: String,
  @SerializedName("activity_id") val activityId: Long?
)
