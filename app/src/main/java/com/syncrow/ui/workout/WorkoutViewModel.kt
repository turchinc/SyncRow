package com.syncrow.ui.workout

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.*
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.exceptions.BleScanException
import com.polidea.rxandroidble3.scan.ScanSettings
import com.syncrow.R
import com.syncrow.data.db.*
import com.syncrow.data.StravaRepository
import com.syncrow.hal.ble.BleHeartRateMonitor
import com.syncrow.hal.ble.FtmsRowingMachine
import com.syncrow.hal.IRowingMachine
import com.syncrow.hal.IHeartRateMonitor
import com.syncrow.hal.RowerMetrics
import com.syncrow.util.DataSmoother
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import java.util.*

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

sealed class ToastEvent {
    data class Resource(val resId: Int, val args: List<Any> = emptyList()) : ToastEvent()
    data class String(val message: kotlin.String) : ToastEvent()
}

class WorkoutViewModel(
    application: Application,
    private val rxBleClient: RxBleClient,
    private val userDao: UserDao,
    private val workoutDao: WorkoutDao,
    private val metricPointDao: MetricPointDao,
    private val stravaRepository: StravaRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("syncrow_prefs", Context.MODE_PRIVATE)
    private val KEY_LAST_USER_ID = "last_user_id"
    private val KEY_ROWER_PREFIX = "rower_addr_"
    private val KEY_HRM_PREFIX = "hrm_addr_"

    private val FTMS_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val HRM_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    private val rowingMachine: IRowingMachine = FtmsRowingMachine(rxBleClient)
    private val heartRateMonitor: IHeartRateMonitor = BleHeartRateMonitor(rxBleClient)
    
    private val powerSmoother = DataSmoother(windowSize = 3)
    private val paceSmoother = DataSmoother(windowSize = 3)
    private val heartRateSmoother = DataSmoother(windowSize = 5)

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

    private val _toastEvent = MutableSharedFlow<ToastEvent>()
    val toastEvent = _toastEvent.asSharedFlow()

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
        
        // Seeding with currently linked devices
        val initialList = mutableListOf<DiscoveredDevice>()
        selectedRowerAddress.value?.let { addr ->
            initialList.add(DiscoveredDevice("Linked Rower", addr, 0, DeviceType.ROWER))
        }
        selectedHrmAddress.value?.let { addr ->
            initialList.add(DiscoveredDevice("Linked HRM", addr, 0, DeviceType.HRM))
        }
        _discoveredDevices.value = initialList
        
        scanJob = viewModelScope.launch {
            rxBleClient.scanBleDevices(
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            ).asFlow()
                .catch { e ->
                    Log.e("WorkoutViewModel", "Scan error: ${e.message}")
                    _isScanning.value = false
                    if (e is BleScanException && e.reason == BleScanException.BLUETOOTH_DISABLED) {
                        _toastEvent.emit(ToastEvent.Resource(R.string.error_bluetooth_disabled))
                    } else {
                        _toastEvent.emit(ToastEvent.Resource(R.string.error_rower_connection_failed, listOf(e.message ?: "Unknown")))
                    }
                }
                .sample(500)
                .collect { result ->
                    val address = result.bleDevice.macAddress
                    val scanName = result.scanRecord.deviceName ?: result.bleDevice.name ?: "Unknown Device"
                    val serviceUuids = result.scanRecord.serviceUuids?.map { it.uuid } ?: emptyList()
                    
                    val currentList = _discoveredDevices.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.address == address }
                    
                    // Detection logic
                    var type = when {
                        address == selectedRowerAddress.value -> DeviceType.ROWER
                        address == selectedHrmAddress.value -> DeviceType.HRM
                        serviceUuids.contains(FTMS_SERVICE_UUID) -> DeviceType.ROWER
                        serviceUuids.contains(HRM_SERVICE_UUID) -> DeviceType.HRM
                        scanName.startsWith("FS-", ignoreCase = true) -> DeviceType.ROWER
                        scanName.contains("Polar", ignoreCase = true) -> DeviceType.HRM
                        scanName.contains("Garmin", ignoreCase = true) -> DeviceType.HRM
                        scanName.contains("Wahoo", ignoreCase = true) -> DeviceType.HRM
                        scanName.contains("Tickr", ignoreCase = true) -> DeviceType.HRM
                        scanName.contains("HR-", ignoreCase = true) -> DeviceType.HRM
                        scanName.contains("Heart", ignoreCase = true) -> DeviceType.HRM
                        scanName.contains("CooSpo", ignoreCase = true) -> DeviceType.HRM
                        scanName.contains("Magene", ignoreCase = true) -> DeviceType.HRM
                        scanName.contains("BLE-", ignoreCase = true) -> DeviceType.HRM
                        else -> DeviceType.UNKNOWN
                    }

                    // Prevent downgrading type if already known
                    if (type == DeviceType.UNKNOWN && existingIndex != -1) {
                        type = currentList[existingIndex].type
                    }

                    val finalName = if (scanName == "Unknown Device" && existingIndex != -1) {
                        currentList[existingIndex].name
                    } else {
                        scanName
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
        
        if (selectedRowerAddress.value == null) {
            viewModelScope.launch {
                _toastEvent.emit(ToastEvent.Resource(R.string.error_no_rower_selected))
            }
            return
        }

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
            rowingMachine.getMetricsFlow(_selectedRowerAddress.value)
                .conflate() // Ensure we only process the latest data and don't backlog
                .catch { e ->
                    Log.e("WorkoutViewModel", "Metrics collection error: ${e.message}")
                    _toastEvent.emit(ToastEvent.Resource(R.string.error_rower_connection_failed, listOf(e.message ?: "Unknown")))
                }.collect { rawData ->
                    val smoothedPower = powerSmoother.add(rawData.power.toDouble()).toInt()
                    val smoothedPace = paceSmoother.add(rawData.pace.toDouble()).toInt()
                    _displayMetrics.value = _displayMetrics.value.copy(power = smoothedPower, pace = smoothedPace, strokeRate = rawData.strokeRate, distance = rawData.distance)
                }
        }
    }

    private fun startHeartRateCollection(address: String? = null) {
        hrJob?.cancel()
        hrJob = viewModelScope.launch {
            heartRateMonitor.getHeartRateFlow(address ?: _selectedHrmAddress.value)
                .conflate()
                .catch { e ->
                    Log.e("WorkoutViewModel", "HR collection error: ${e.message}")
                }.collect { bpm ->
                    val finalBpm = if (bpm > 0) {
                        heartRateSmoother.add(bpm.toDouble()).toInt()
                    } else {
                        0
                    }
                    _displayMetrics.value = _displayMetrics.value.copy(heartRate = finalBpm)
                }
        }
    }

    private fun startRecording() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
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

    fun completeStravaAuth(code: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                stravaRepository.connect(code, user)
                _toastEvent.emit(ToastEvent.Resource(R.string.strava_connected_success))
                // Reload user to get updated tokens
                loadUser()
            } catch (e: Exception) {
                _toastEvent.emit(ToastEvent.Resource(R.string.strava_connection_error))
            }
        }
    }

    fun disconnectStrava() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updatedUser = user.copy(
                stravaToken = null,
                stravaRefreshToken = null,
                stravaTokenExpiresAt = null
            )
            userDao.updateUser(updatedUser)
            setCurrentUserInternal(updatedUser)
            _toastEvent.emit(ToastEvent.Resource(R.string.strava_disconnected_success))
        }
    }

    fun syncWorkoutToStrava(workoutId: Long) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val workout = workoutDao.getWorkoutById(workoutId) ?: return@launch
            val points = metricPointDao.getPointsForWorkout(workoutId).first()
            
            _toastEvent.emit(ToastEvent.Resource(R.string.strava_syncing))
            val success = stravaRepository.uploadWorkout(workout, points, user)
            if (success) {
                _toastEvent.emit(ToastEvent.Resource(R.string.strava_sync_success))
            } else {
                _toastEvent.emit(ToastEvent.Resource(R.string.strava_sync_error))
            }
        }
    }

    class Factory(
        private val application: Application, 
        private val rxBleClient: RxBleClient, 
        private val userDao: UserDao, 
        private val workoutDao: WorkoutDao, 
        private val metricPointDao: MetricPointDao,
        private val stravaRepository: StravaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = 
            WorkoutViewModel(application, rxBleClient, userDao, workoutDao, metricPointDao, stravaRepository) as T
    }
}
