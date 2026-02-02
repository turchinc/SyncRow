package com.viberow.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viberow.hal.IRowingMachine
import com.viberow.hal.RowerMetrics
import com.viberow.hal.mock.MockRower
import com.viberow.util.DataSmoother
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class WorkoutViewModel : ViewModel() {

    // The data source can be easily swapped here (e.g., to a real hardware class)
    private val rowingMachine: IRowingMachine = MockRower()

    // Smoothers for the metrics that need it
    private val powerSmoother = DataSmoother(windowSize = 3)
    private val paceSmoother = DataSmoother(windowSize = 3)

    // A private mutable state flow to hold the UI-ready data
    private val _displayMetrics = MutableStateFlow(RowerMetrics(0, 0, 0, 0))
    // A public, read-only state flow for the UI to observe
    val displayMetrics: StateFlow<RowerMetrics> = _displayMetrics.asStateFlow()

    init {
        // Launch a coroutine to listen to the data stream from the rower
        viewModelScope.launch {
            rowingMachine.getMetricsFlow().collect { rawData ->
                // Apply smoothing to the raw data
                val smoothedPower = powerSmoother.add(rawData.power.toDouble()).toInt()
                val smoothedPace = paceSmoother.add(rawData.pace.toDouble()).toInt()

                // Update the state that the UI observes with the smoothed data
                _displayMetrics.value = rawData.copy(
                    power = smoothedPower,
                    pace = smoothedPace
                )
            }
        }
    }
}