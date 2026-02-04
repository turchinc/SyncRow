package com.syncrow.hal.ble

import android.util.Log
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import com.syncrow.hal.IRowingMachine
import com.syncrow.hal.RowerMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx3.asFlow
import java.util.UUID
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class FtmsRowingMachine(
    private val rxBleClient: RxBleClient
) : IRowingMachine {

    private val FTMS_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val ROWER_DATA_CHAR_UUID = UUID.fromString("00002ad1-0000-1000-8000-00805f9b34fb")
    private val CONTROL_POINT_CHAR_UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")

    // State persistence
    private var lastMetrics = RowerMetrics(0, 0, 0, 0)

    override fun getMetricsFlow(targetAddress: String?): Flow<RowerMetrics> {
        val scanFilter = ScanFilter.Builder()
        if (targetAddress != null) {
            scanFilter.setDeviceAddress(targetAddress)
        }

        return rxBleClient.scanBleDevices(
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanFilter.build()
        )
            .filter { result ->
                if (targetAddress != null) return@filter true 
                val name = result.bleDevice.name ?: ""
                val hasFtms = result.scanRecord.serviceUuids?.contains(android.os.ParcelUuid(FTMS_SERVICE_UUID)) ?: false
                name.startsWith("FS-", ignoreCase = true) || name.contains("Styrke", ignoreCase = true) || name.contains("Skandika", ignoreCase = true) || hasFtms
            }
            .firstElement()
            .toObservable()
            .flatMap { scanResult ->
                Log.d("FtmsRower", "Connecting to Rower: ${scanResult.bleDevice.macAddress}")
                Observable.timer(1000, TimeUnit.MILLISECONDS)
                    .flatMap { scanResult.bleDevice.establishConnection(false) }
                    .retry(3)
            }
            .flatMap { connection ->
                setupFtmsHandshake(connection).andThen(Observable.just(connection))
            }
            .flatMap { connection ->
                connection.setupNotification(ROWER_DATA_CHAR_UUID)
            }
            .flatMap { it }
            .map { bytes ->
                parseRowerData(bytes)
            }
            .onErrorResumeNext { Observable.empty() }
            .asFlow()
    }

    private fun setupFtmsHandshake(connection: RxBleConnection): io.reactivex.rxjava3.core.Completable {
        return connection.writeCharacteristic(CONTROL_POINT_CHAR_UUID, byteArrayOf(0x00))
            .flatMap { connection.writeCharacteristic(CONTROL_POINT_CHAR_UUID, byteArrayOf(0x07)) }
            .ignoreElement()
    }

    /**
     * Parses Rowing Machine Data (0x2AD1) using the dynamic FTMS bit-flag specification.
     * Verified against raw byte logs.
     */
    private fun parseRowerData(bytes: ByteArray): RowerMetrics {
        if (bytes.size < 2) return lastMetrics
        
        val flags = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        var offset = 2
        
        var strokeRate = lastMetrics.strokeRate
        var distance = lastMetrics.distance
        var pace = lastMetrics.pace
        var power = lastMetrics.power

        try {
            // Bit 0: More Data. If 0, Stroke Rate (UINT8) and Stroke Count (UINT16) are present.
            if (flags and 0x01 == 0) {
                if (bytes.size >= offset + 3) {
                    strokeRate = (bytes[offset].toInt() and 0xFF) / 2
                    // Skip Stroke Count (UINT16)
                    offset += 3
                }
            }

            // Bit 1: Average Speed (UINT16)
            if (flags and 0x02 != 0) offset += 2

            // Bit 2: Total Distance (UINT24) - Crucial 3-byte field
            if (flags and 0x04 != 0) {
                if (bytes.size >= offset + 3) {
                    distance = (bytes[offset].toInt() and 0xFF) or 
                               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or 
                               ((bytes[offset + 2].toInt() and 0xFF) shl 16)
                    offset += 3
                }
            }

            // Bit 3: Instantaneous Pace (UINT16)
            if (flags and 0x08 != 0) {
                if (bytes.size >= offset + 2) {
                    val rawPace = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                    pace = if (rawPace == 0xFFFF) 0 else rawPace
                    offset += 2
                }
            }

            // Bit 4: Average Pace (UINT16)
            if (flags and 0x10 != 0) offset += 2

            // Bit 5: Instantaneous Power (SINT16)
            if (flags and 0x20 != 0) {
                if (bytes.size >= offset + 2) {
                    power = (bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)
                    offset += 2
                }
            }
            
            lastMetrics = RowerMetrics(power, pace, strokeRate, distance)
            
        } catch (e: Exception) {
            val hexString = bytes.joinToString("") { "%02X".format(it) }
            Log.e("FtmsRower", "Dynamic parse error at offset $offset in $hexString", e)
        }

        return lastMetrics
    }
}
