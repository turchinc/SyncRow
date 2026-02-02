package com.syncrow.hal.mock

import com.syncrow.hal.IRowingMachine
import com.syncrow.hal.RowerMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin
import kotlin.random.Random

class MockRower : IRowingMachine {
    private var time = 0.0
    private val targetWatts = 100.0
    private val targetPaceSeconds = 125.0

    override fun getMetricsFlow(targetAddress: String?): Flow<RowerMetrics> = flow {
        var simulatedDistance = 0.0
        while (true) {
            val sineValue = sin(time)
            val currentPower = targetWatts + (sineValue * 5) + Random.nextDouble(-1.0, 1.0)
            val currentPace = targetPaceSeconds - (sineValue * 2) + Random.nextDouble(-0.5, 0.5)
            val currentSpm = 22.0 + (sineValue * 2)
            simulatedDistance += 500.0 / currentPace

            emit(RowerMetrics(
                power = currentPower.toInt(),
                pace = currentPace.toInt(),
                strokeRate = currentSpm.toInt(),
                distance = simulatedDistance.toInt()
            ))

            time += 0.5
            delay(1000L)
        }
    }
}
