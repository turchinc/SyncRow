package com.syncrow.hal.ble

import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.scan.ScanSettings
import com.polidea.rxandroidble3.scan.ScanFilter
import com.syncrow.hal.IHeartRateMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx3.asFlow
import java.util.UUID
import io.reactivex.rxjava3.core.Observable
import android.util.Log
import java.util.concurrent.TimeUnit

class BleHeartRateMonitor(
    private val rxBleClient: RxBleClient
) : IHeartRateMonitor {

    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    override fun getHeartRateFlow(targetAddress: String?): Flow<Int> {
        val scanFilterBuilder = ScanFilter.Builder()
        
        if (targetAddress != null && targetAddress.isNotEmpty()) {
            // If we have a specific address, filter by that. 
            // Don't strictly require the service UUID in the advertisement as some devices omit it.
            scanFilterBuilder.setDeviceAddress(targetAddress)
        } else {
            // If scanning for any HRM, we must use the service UUID.
            scanFilterBuilder.setServiceUuid(android.os.ParcelUuid(HEART_RATE_SERVICE_UUID))
        }

        return rxBleClient.scanBleDevices(
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanFilterBuilder.build()
        )
            .firstElement()
            .toObservable()
            .flatMap { scanResult ->
                Log.d("BleHRM", "Connecting to HRM: ${scanResult.bleDevice.macAddress} (${scanResult.bleDevice.name})")
                scanResult.bleDevice.establishConnection(false)
            }
            .flatMap { connection ->
                connection.setupNotification(HEART_RATE_MEASUREMENT_CHAR_UUID)
            }
            .flatMap { it }
            .map { bytes ->
                parseHeartRate(bytes)
            }
            .retryWhen { errors ->
                errors.flatMap { throwable ->
                    Log.e("BleHRM", "HRM Error, retrying in 5s...", throwable)
                    Observable.timer(5, TimeUnit.SECONDS)
                }
            }
            .asFlow()
    }

    private fun parseHeartRate(bytes: ByteArray): Int {
        if (bytes.isEmpty()) return 0
        val flag = bytes[0].toInt()
        val format = flag and 0x01
        return if (format == 0) {
            bytes[1].toInt() and 0xFF
        } else {
            if (bytes.size < 3) return 0
            ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        }
    }
}
