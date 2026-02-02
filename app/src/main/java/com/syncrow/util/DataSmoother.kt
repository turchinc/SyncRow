package com.syncrow.util

import java.util.LinkedList
import java.util.Queue

/**
 * A utility class to calculate the simple moving average for a stream of numbers.
 * This helps to smooth out noisy sensor data for a more stable UI display.
 *
 * @param windowSize The number of recent values to include in the average.
 */
class DataSmoother(private val windowSize: Int = 3) { // Default: average over the last 3 seconds
    private val dataPoints: Queue<Double> = LinkedList()
    private var sum = 0.0

    /**
     * Adds a new data point to the window and returns the updated average.
     */
    fun add(value: Double): Double {
        if (dataPoints.size >= windowSize) {
            sum -= dataPoints.poll() // Remove the oldest value
        }
        dataPoints.add(value)
        sum += value
        return getAverage()
    }

    /**
     * Returns the current moving average.
     */
    fun getAverage(): Double {
        return if (dataPoints.isEmpty()) 0.0 else sum / dataPoints.size
    }
}