package com.viberow.hal.mock

import com.viberow.hal.IRowingMachine
import com.viberow.hal.RowerMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A mock implementation of a rowing machine that generates synthetic data.
 * Ideal for UI development and testing without physical hardware.
 */
class MockRower : IRowingMachine {

    private var time = 0.0

    // Target values for a realistic cruising state
    private val targetWatts = 100.0
    private val targetPaceSeconds = 125.0 // 2:05 in seconds

    /**
     * Emits a new set of metrics every second, simulating a live workout.
     * Uses a sine wave to create a gentle, rhythmic fluctuation.
     */
    override fun getMetricsFlow(): Flow<RowerMetrics> = flow {
        var simulatedDistance = 0.0
        while (true) {
            // Sine wave makes the data feel more natural and rhythmic
            val sineValue = sin(time)

            val currentPower = targetWatts + (sineValue * 5) + Random.nextDouble(-1.0, 1.0)
            val currentPace = targetPaceSeconds - (sineValue * 2) + Random.nextDouble(-0.5, 0.5)
            val currentSpm = 22.0 + (sineValue * 2) // Fluctuate between 20-24 SPM

            // Increment distance based on a realistic pace
            // Pace is sec/500m, so meters per second is 500 / pace
            simulatedDistance += 500.0 / currentPace

            emit(
                RowerMetrics(
                    power = currentPower.toInt(),
                    pace = currentPace.toInt(),
                    strokeRate = currentSpm.toInt(),
                    distance = simulatedDistance.toInt()
                )
            )

            time += 0.5 // Controls the speed of the sine wave
            delay(1000L) // Emit data once per second
        }
    }
}