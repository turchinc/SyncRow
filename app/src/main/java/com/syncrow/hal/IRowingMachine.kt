package com.syncrow.hal

import kotlinx.coroutines.flow.Flow

/**
 * The Hardware Abstraction Layer for any rowing machine.
 * Defines the contract for providing rowing data as a reactive stream.
 */
interface IRowingMachine {
    /**
     * A cold Flow that emits RowerMetrics updates.
     * @param targetAddress Optional MAC address to connect to a specific machine.
     */
    fun getMetricsFlow(targetAddress: String? = null): Flow<RowerMetrics>
}

/**
 * A data class to hold a single snapshot of all relevant rowing metrics.
 */
data class RowerMetrics(
    val power: Int,
    val pace: Int,
    val strokeRate: Int,
    val distance: Int,
    val heartRate: Int = 0
)
