package com.syncrow.ui.workout

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.scan.ScanSettings
import com.syncrow.data.db.*
import com.syncrow.hal.IRowingMachine
import com.syncrow.hal.IHeartRateMonitor
import com.syncrow.hal.RowerMetrics
import com.syncrow.hal.mock.MockRower
import com.syncrow.hal.ble.BleHeartRateMonitor
import com.syncrow.hal.ble.FtmsRowingMachine
import com.syncrow.util.DataSmoother
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

enum class SessionState {
    IDLE, ROWING, PAUSED
}

enum class DeviceType {
    ROWER, HRM, UNKNOWN
}

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val type: DeviceType
)

class WorkoutViewModel(
    application: Application,
    private val rxBleClient: RxBleClient,
    private val userDao: UserDao,
    private val workoutDao: WorkoutDao,
    private val metricPointDao: MetricPointDao
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("syncrow_prefs", Context.MODE_PRIVATE)
    private val KEY_LAST_USER_ID = "last_user_id"
    private val KEY_ROWER_PREFIX = "rower_addr_"
    private val KEY_HRM_PREFIX = "hrm_addr_"

    private val FTMS_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val HRM_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    private val useRealHardware = true
    private val rowingMachine: IRowingMachine = if (useRealHardware) FtmsRowingMachine(rxBleClient) else MockRower()
    private val heartRateMonitor: IHeartRateMonitor = BleHeartRateMonitor(rxBleClient)
    
    private val powerSmoother = DataSmoother(windowSize = 3)
    private val paceSmoother = DataSmoother(windowSize = 3)

    private val _displayMetrics = MutableStateFlow(RowerMetrics(0, 0, 0, 0, 0))
    val displayMetrics: StateFlow<RowerMetrics> = _displayMetrics.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    val allUsers = userDao.getAllUsers()
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedRowerAddress = MutableStateFlow<String?>(null)
    val selectedRowerAddress: StateFlow<String?> = _selectedRowerAddress.asStateFlow()

    private val _selectedHrmAddress = MutableStateFlow<String?>(null)
    val selectedHrmAddress: StateFlow<String?> = _selectedHrmAddress.asStateFlow()

    private var currentWorkoutId: Long? = null
    private var timerJob: Job? = null
    private var metricsJob: Job? = null
    private var hrJob: Job? = null
    private var recordingJob: Job? = null
    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null

    init {
        loadUser()
        // Automatically restart HR collection when the selected HRM address changes
        viewModelScope.launch {
            selectedHrmAddress.collect { address ->
                if (address != null) {
                    Log.d("WorkoutViewModel", "HRM address changed to: $address. Restarting collection.")
                    startHeartRateCollection(address)
                }
            }
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            val users = allUsers.first()
            if (users.isEmpty()) {
                val defaultId = userDao.insertUser(User(name = "Chris"))
                val user = userDao.getUserById(defaultId)
                setCurrentUserInternal(user)
            } else {
                val lastUserId = prefs.getLong(KEY_LAST_USER_ID, -1L)
                val user = if (lastUserId != -1L) {
                    userDao.getUserById(lastUserId) ?: users.first()
                } else {
                    users.first()
                }
                setCurrentUserInternal(user)
            }
        }
    }

    private fun setCurrentUserInternal(user: User?) {
        _currentUser.value = user
        user?.let {
            prefs.edit().putLong(KEY_LAST_USER_ID, it.id).apply()
            applyLanguage(it.languageCode)
            
            // Load saved devices for this user
            _selectedRowerAddress.value = prefs.getString("$KEY_ROWER_PREFIX${it.id}", null)
            _selectedHrmAddress.value = prefs.getString("$KEY_HRM_PREFIX${it.id}", null)
        }
    }

    private fun applyLanguage(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun addUser(name: String) {
        viewModelScope.launch {
            userDao.insertUser(User(name = name))
        }
    }

    fun updateCurrentUser(user: User) {
        viewModelScope.launch {
            userDao.updateUser(user)
            setCurrentUserInternal(user)
        }
    }

    fun switchUser(user: User) {
        setCurrentUserInternal(user)
        // No need to clear addresses manually; setCurrentUserInternal loads the new user's saved ones
    }

    fun getWorkoutsForCurrentUser(): Flow<List<Workout>> {
        return currentUser.flatMapLatest { user ->
            user?.id?.let { workoutDao.getWorkoutsForUser(it) } ?: emptyFlow()
        }
    }

    suspend fun getWorkoutById(id: Long): Workout? {
        return workoutDao.getWorkoutById(id)
    }

    fun deleteWorkout(workoutId: Long) {
        viewModelScope.launch {
            val workout = workoutDao.getWorkoutById(workoutId)
            if (workout != null) {
                workoutDao.deleteWorkout(workout)
            }
        }
    }

    fun deleteWorkouts(workoutIds: List<Long>) {
        viewModelScope.launch {
            workoutIds.forEach { id ->
                val workout = workoutDao.getWorkoutById(id)
                if (workout != null) {
                    workoutDao.deleteWorkout(workout)
                }
            }
        }
    }

    fun startDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        
        scanJob = viewModelScope.launch {
            rxBleClient.scanBleDevices(
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            ).asFlow().collect { result ->
                val address = result.bleDevice.macAddress
                val name = result.bleDevice.name ?: "Unknown Device"
                val serviceUuids = result.scanRecord.serviceUuids?.map { it.uuid } ?: emptyList()
                
                val currentList = _discoveredDevices.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.address == address }
                
                // Detection logic
                var type = when {
                    serviceUuids.contains(FTMS_SERVICE_UUID) -> DeviceType.ROWER
                    serviceUuids.contains(HRM_SERVICE_UUID) -> DeviceType.HRM
                    name.startsWith("FS-", ignoreCase = true) -> DeviceType.ROWER
                    name.contains("Polar", ignoreCase = true) -> DeviceType.HRM
                    name.contains("Garmin", ignoreCase = true) -> DeviceType.HRM
                    name.contains("Wahoo", ignoreCase = true) -> DeviceType.HRM
                    name.contains("Tickr", ignoreCase = true) -> DeviceType.HRM
                    name.contains("HR-", ignoreCase = true) -> DeviceType.HRM
                    name.contains("Heart", ignoreCase = true) -> DeviceType.HRM
                    name.contains("CooSpo", ignoreCase = true) -> DeviceType.HRM
                    name.contains("Magene", ignoreCase = true) -> DeviceType.HRM
                    else -> DeviceType.UNKNOWN
                }

                // Sticky type
                if (type == DeviceType.UNKNOWN && existingIndex != -1) {
                    val existingType = currentList[existingIndex].type
                    if (existingType != DeviceType.UNKNOWN) {
                        type = existingType
                    }
                }

                val finalName = if (name == "Unknown Device" && existingIndex != -1) {
                    currentList[existingIndex].name
                } else {
                    name
                }

                val newDevice = DiscoveredDevice(finalName, address, result.rssi, type)
                
                if (existingIndex != -1) {
                    currentList[existingIndex] = newDevice
                } else {
                    currentList.add(newDevice)
                }
                _discoveredDevices.value = currentList.sortedByDescending { it.rssi }
            }
        }
        
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch { 
            delay(15000)
            stopDiscovery() 
        }
    }

    fun stopDiscovery() { 
        scanJob?.cancel()
        scanTimeoutJob?.cancel()
        _isScanning.value = false 
    }

    fun selectDevice(device: DiscoveredDevice) {
        val user = _currentUser.value ?: return
        when (device.type) {
            DeviceType.ROWER -> {
                _selectedRowerAddress.value = device.address
                prefs.edit().putString("$KEY_ROWER_PREFIX${user.id}", device.address).apply()
            }
            DeviceType.HRM -> {
                _selectedHrmAddress.value = device.address
                prefs.edit().putString("$KEY_HRM_PREFIX${user.id}", device.address).apply()
            }
            else -> {}
        }
    }

    fun startWorkout() {
        if (_sessionState.value == SessionState.ROWING) return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            if (_sessionState.value == SessionState.IDLE) {
                currentWorkoutId = workoutDao.insertWorkout(Workout(userId = user.id, startTime = System.currentTimeMillis()))
                startRecording()
            }
            _sessionState.value = SessionState.ROWING
            startTimer()
            startMetricsCollection()
        }
    }

    fun pauseWorkout() { _sessionState.value = SessionState.PAUSED; stopWorkoutJobs() }

    fun finishWorkout(save: Boolean) {
        val workoutId = currentWorkoutId
        viewModelScope.launch {
            stopWorkoutJobs() 
            
            if (workoutId != null) {
                if (save) {
                    val workout = workoutDao.getWorkoutById(workoutId)
                    if (workout != null) {
                        val metrics = _displayMetrics.value
                        workout.endTime = System.currentTimeMillis()
                        workout.totalDistanceMeters = metrics.distance
                        workout.totalSeconds = _elapsedSeconds.value
                        
                        val points = metricPointDao.getPointsForWorkout(workoutId).first()
                        if (points.isNotEmpty()) {
                            workout.avgPower = points.map { it.power }.average().toInt()
                            workout.avgHeartRate = points.filter { it.heartRate > 0 }.map { it.heartRate }.average().toInt()
                        } else {
                            workout.avgPower = metrics.power
                            workout.avgHeartRate = metrics.heartRate
                        }
                        
                        workoutDao.updateWorkout(workout)
                        Log.d("WorkoutViewModel", "Workout saved: ID $workoutId, Dist ${workout.totalDistanceMeters}m")
                    }
                } else {
                    val workout = workoutDao.getWorkoutById(workoutId)
                    if (workout != null) {
                        workoutDao.deleteWorkout(workout)
                        Log.d("WorkoutViewModel", "Workout deleted: ID $workoutId")
                    }
                }
            }
            _sessionState.value = SessionState.IDLE
            _elapsedSeconds.value = 0
            _displayMetrics.value = RowerMetrics(0, 0, 0, 0, _displayMetrics.value.heartRate)
            currentWorkoutId = null
        }
    }

    private fun stopWorkoutJobs() { timerJob?.cancel(); metricsJob?.cancel(); recordingJob?.cancel() }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch { while (true) { delay(1000); _elapsedSeconds.value += 1 } }
    }

    private fun startMetricsCollection() {
        metricsJob?.cancel()
        metricsJob = viewModelScope.launch {
            rowingMachine.getMetricsFlow(_selectedRowerAddress.value).collect { rawData ->
                val smoothedPower = powerSmoother.add(rawData.power.toDouble()).toInt()
                val smoothedPace = paceSmoother.add(rawData.pace.toDouble()).toInt()
                _displayMetrics.value = _displayMetrics.value.copy(power = smoothedPower, pace = smoothedPace, strokeRate = rawData.strokeRate, distance = rawData.distance)
            }
        }
    }

    private fun startHeartRateCollection(address: String? = null) {
        hrJob?.cancel()
        hrJob = viewModelScope.launch {
            heartRateMonitor.getHeartRateFlow(address ?: _selectedHrmAddress.value).collect { bpm ->
                _displayMetrics.value = _displayMetrics.value.copy(heartRate = bpm)
            }
        }
    }

    private fun startRecording() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val workoutId = currentWorkoutId ?: break
                val m = _displayMetrics.value
                metricPointDao.insertMetricPoint(MetricPoint(workoutId = workoutId, timestamp = System.currentTimeMillis(), power = m.power, pace = m.pace, strokeRate = m.strokeRate, distance = m.distance, heartRate = m.heartRate))
            }
        }
    }

    fun getMetricPointsForWorkout(workoutId: Long): Flow<List<MetricPoint>> {
        return metricPointDao.getPointsForWorkout(workoutId)
    }

    class Factory(private val application: Application, private val rxBleClient: RxBleClient, private val userDao: UserDao, private val workoutDao: WorkoutDao, private val metricPointDao: MetricPointDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkoutViewModel(application, rxBleClient, userDao, workoutDao, metricPointDao) as T
    }
}
