package com.syncrow.hal

import kotlinx.coroutines.flow.Flow

/** Interface for heart rate monitor hardware. */
interface IHeartRateMonitor {
  /**
   * Emits heart rate in BPM.
   *
   * @param targetAddress Optional MAC address to connect to a specific device.
   */
  fun getHeartRateFlow(targetAddress: String? = null): Flow<Int>
}
