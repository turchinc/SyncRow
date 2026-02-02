package com.syncrow

import android.app.Application
import android.util.Log
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.exceptions.BleDisconnectedException
import com.syncrow.data.db.SyncRowDatabase
import io.reactivex.rxjava3.plugins.RxJavaPlugins

class SyncRowApplication : Application() {
    lateinit var rxBleClient: RxBleClient
        private set
    
    lateinit var database: SyncRowDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Handle RxJava undeliverable exceptions to prevent crashes on BLE disconnects
        RxJavaPlugins.setErrorHandler { throwable ->
            Log.w("SyncRowApp", "Undeliverable exception received: ${throwable.message}")
        }

        rxBleClient = RxBleClient.create(this)
        database = SyncRowDatabase.getDatabase(this)
    }
}
