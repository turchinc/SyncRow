package com.syncrow

import android.app.Application
import android.util.Log
import com.polidea.rxandroidble3.RxBleClient
import com.syncrow.data.CloudSyncManager
import com.syncrow.data.StravaRepository
import com.syncrow.data.api.StravaApi
import com.syncrow.data.db.SyncRowDatabase
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SyncRowApplication : Application() {
  lateinit var rxBleClient: RxBleClient
    private set

  lateinit var database: SyncRowDatabase
    private set

  lateinit var stravaRepository: StravaRepository
    private set

  lateinit var cloudSyncManager: CloudSyncManager
    private set

  override fun onCreate() {
    super.onCreate()

    // Handle RxJava undeliverable exceptions to prevent crashes on BLE disconnects
    RxJavaPlugins.setErrorHandler { throwable ->
      Log.w("SyncRowApp", "Undeliverable exception received: ${throwable.message}")
    }

    rxBleClient = RxBleClient.create(this)
    database = SyncRowDatabase.getDatabase(this)

    val retrofit =
      Retrofit.Builder()
        .baseUrl("https://www.strava.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val stravaApi = retrofit.create(StravaApi::class.java)

    stravaRepository = StravaRepository(this, stravaApi, database.userDao(), database.workoutDao())
    cloudSyncManager =
      CloudSyncManager(
        database.userDao(),
        database.workoutDao(),
        database.trainingDao(),
        database.splitDao()
      )
  }
}
