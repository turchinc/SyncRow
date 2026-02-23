package com.syncrow.hal.ble

import android.util.Log
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import com.syncrow.hal.IRowingMachine
import com.syncrow.hal.RowerMetrics
import io.reactivex.rxjava3.core.Observable
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx3.asFlow

class FtmsRowingMachine(private val rxBleClient: RxBleClient) : IRowingMachine {

  companion object {
    // Concept2 wattage calculation constants
    private const val CONCEPT2_WATTS_CONSTANT = 2.80
    private const val METERS_PER_SPLIT = 500.0
  }

  private val FTMS_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
  private val ROWER_DATA_CHAR_UUID = UUID.fromString("00002ad1-0000-1000-8000-00805f9b34fb")
  private val CONTROL_POINT_CHAR_UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")

  // State persistence
  private var lastMetrics = RowerMetrics(0, 0, 0, 0, 0)

  /**
   * If true, wattage is calculated using the Concept2-standard physics formula from pace. If false,
   * the machine's reported wattage is used directly.
   */
  var useConcept2Watts: Boolean = true

  override fun getMetricsFlow(targetAddress: String?): Flow<RowerMetrics> {
    val scanFilter = ScanFilter.Builder()
    if (targetAddress != null) {
      scanFilter.setDeviceAddress(targetAddress)
    }

    return rxBleClient
      .scanBleDevices(
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
        scanFilter.build()
      )
      .filter { result ->
        if (targetAddress != null) return@filter true
        val name = result.bleDevice.name ?: ""
        val hasFtms =
          result.scanRecord.serviceUuids?.contains(android.os.ParcelUuid(FTMS_SERVICE_UUID))
            ?: false
        name.startsWith("FS-", ignoreCase = true) ||
          name.contains("Styrke", ignoreCase = true) ||
          name.contains("Skandika", ignoreCase = true) ||
          hasFtms
      }
      .firstElement()
      .toObservable()
      .flatMap { scanResult ->
        Log.d("FtmsRower", "Connecting to Rower: ${scanResult.bleDevice.macAddress}")
        Observable.timer(1000, TimeUnit.MILLISECONDS)
          .flatMap { scanResult.bleDevice.establishConnection(false) }
          .retry(3)
      }
      .flatMap { connection -> setupFtmsHandshake(connection).andThen(Observable.just(connection)) }
      .flatMap { connection -> connection.setupNotification(ROWER_DATA_CHAR_UUID) }
      .flatMap { it }
      .map { bytes -> parseRowerData(bytes) }
      .onErrorResumeNext { Observable.empty() }
      .asFlow()
  }

  private fun setupFtmsHandshake(
    connection: RxBleConnection
  ): io.reactivex.rxjava3.core.Completable {
    return connection
      .writeCharacteristic(CONTROL_POINT_CHAR_UUID, byteArrayOf(0x00))
      .flatMap { connection.writeCharacteristic(CONTROL_POINT_CHAR_UUID, byteArrayOf(0x07)) }
      .ignoreElement()
  }

  /**
   * Calculates Concept2-standard wattage from pace using the standard physics formula. Formula:
   * Watts = 2.80 / (P^3), where P = pace in seconds per meter.
   *
   * @param paceSeconds Pace in seconds per 500m. Returns 0 if pace is 0 or invalid.
   * @return Calculated watts as Int.
   */
  private fun calculateConcept2Watts(paceSeconds: Int): Int {
    if (paceSeconds <= 0) return 0

    // Convert split time to pace in seconds per meter
    // P = seconds per 500m / 500 = seconds per meter
    val secondsPerMeter = paceSeconds.toDouble() / METERS_PER_SPLIT

    // Apply Concept2 formula: Watts = 2.80 / (P^3)
    val watts = CONCEPT2_WATTS_CONSTANT / secondsPerMeter.pow(3.0)

    return watts.toInt()
  }

  /**
   * Parses Rowing Machine Data (0x2AD1) using the dynamic FTMS bit-flag specification.
   *
   * Fixed bug: Stroke Rate/Count presence is now correctly checked via the 'More Data' bit (Bit 0).
   * Fixed bug: Offsets are now correctly advanced for all fields, including Average Pace.
   */
  private fun parseRowerData(bytes: ByteArray): RowerMetrics {
    if (bytes.size < 2) return lastMetrics

    // b5 (Byte 0) - Primary Flags
    val b5 = bytes[0].toInt() and 0xFF
    // b6 (Byte 1) - Secondary Flags
    val b6 = bytes[1].toInt() and 0xFF

    var offset = 2

    var strokeRate = lastMetrics.strokeRate
    var distance = lastMetrics.distance
    var pace = lastMetrics.pace
    var power = lastMetrics.power
    var heartRate = lastMetrics.heartRate
    var paceUpdated = false
    var machinePower = 0

    try {
      // 1. Stroke Rate (UINT8) & Stroke Count (UINT16)
      // Spec: These fields are present ONLY IF "More Data" bit (Bit 0) is 0.
      if ((b5 and 0x01) == 0) {
        if (bytes.size >= offset + 3) {
          // Value is stored as x2 (0.5 resolution) per spec.
          // If this is still too high, some machines might be sending direct SPM.
          strokeRate = (bytes[offset].toInt() and 0xFF) / 2
          // Skip Stroke Count (2 bytes)
          offset += 3
        }
      }

      // 2. Average Stroke Rate (UINT8)
      // Presence: Bit 1 of b5
      if ((b5 and 0x02) != 0) {
        offset += 1
      }

      // 3. Total Distance (UINT24)
      // Presence: Bit 2 of b5
      if ((b5 and 0x04) != 0) {
        if (bytes.size >= offset + 3) {
          val rawDistance =
            (bytes[offset].toInt() and 0xFF) or
              ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
              ((bytes[offset + 2].toInt() and 0xFF) shl 16)

          distance = rawDistance
          offset += 3
        }
      }

      // 4. Instantaneous Pace (UINT16)
      // Presence: Bit 3 of b5
      if ((b5 and 0x08) != 0) {
        if (bytes.size >= offset + 2) {
          val rawPace =
            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
          // Only update pace if the value is valid (0xFFFF indicates invalid/no data)
          if (rawPace != 0xFFFF) {
            pace = rawPace
            paceUpdated = true
          }
          offset += 2
        }
      }

      // 5. Average Pace (UINT16)
      // Presence: Bit 4 of b5
      if ((b5 and 0x10) != 0) {
        offset += 2
      }

      // 6. Instantaneous Power (SINT16)
      // Presence: Bit 5 of b5
      if ((b5 and 0x20) != 0) {
        if (bytes.size >= offset + 2) {
          // SINT16 Power
          machinePower = (bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)
          // Power values are often signed; handle negative if necessary
          if (machinePower > 32767) machinePower -= 65536

          if (!useConcept2Watts) {
            power = machinePower
          }
          offset += 2
        }
      }

      // 7. Average Power (SINT16)
      // Presence: Bit 6 of b5
      if ((b5 and 0x40) != 0) {
        offset += 2
      }

      // 8. Resistance Level (SINT16)
      // Presence: Bit 7 of b5
      if ((b5 and 0x80) != 0) {
        offset += 2
      }

      // --- Secondary Flags (b6) ---

      // 9. Energy Fields (Total(2) + PerHour(2) + PerMin(1) = 5 bytes)
      // Presence: Bit 0 of b6
      if ((b6 and 0x01) != 0) {
        offset += 5
      }

      // 10. Heart Rate (UINT8)
      // Presence: Bit 1 of b6
      if ((b6 and 0x02) != 0) {
        if (bytes.size >= offset + 1) {
          heartRate = bytes[offset].toInt() and 0xFF
          offset += 1
        }
      }

      // 11. Elapsed Time (UINT16)
      // Presence: Bit 3 of b6 (0x08)
      if ((b6 and 0x08) != 0) {
        offset += 2
      }

      // 12. Remaining Time (UINT16)
      // Presence: Bit 4 of b6 (0x10)
      if ((b6 and 0x10) != 0) {
        offset += 2
      }

      // Final Power logic:
      // If using Concept2, calculate from pace.
      // If NOT using Concept2, we already set power = machinePower above.
      if (useConcept2Watts && paceUpdated) {
        power = calculateConcept2Watts(pace)
      } else if (!useConcept2Watts && machinePower == 0 && paceUpdated) {
        // Fallback if machine reports 0 watts but we have a pace
        // This handles cases where machine reports flags but sends 0s
        power = calculateConcept2Watts(pace)
      }

      lastMetrics = RowerMetrics(power, pace, strokeRate, distance, heartRate)
    } catch (e: Exception) {
      val hexString = bytes.joinToString("") { "%02X".format(it) }
      Log.e("FtmsRower", "Dynamic parse error at offset $offset in $hexString", e)
    }

    return lastMetrics
  }
}
