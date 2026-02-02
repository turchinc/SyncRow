package com.viberow.hal

import kotlinx.coroutines.flow.Flow

/**
 * The Hardware Abstraction Layer for any rowing machine.
 * Defines the contract for providing rowing data as a reactive stream.
 */
interface IRowingMachine {
    /**
     * A cold Flow that emits RowerMetrics updates.
     * Implementations should handle connecting to and reading from the hardware.
     */
    fun getMetricsFlow(): Flow<RowerMetrics>
}

/**
 * A data class to hold a single snapshot of all relevant rowing metrics.
 *
 * @param power Instantaneous power in Watts.
 * @param pace Instantaneous pace in total seconds per 500m (e.g., 125 for a 2:05 pace).
 * @param strokeRate Strokes per minute (SPM).
 * @param distance Total distance in meters.
 */
data class RowerMetrics(
    val power: Int,
    val pace: Int,
    val strokeRate: Int,
    val distance: Int
)