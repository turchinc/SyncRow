package com.syncrow.ui.workout

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.exceptions.BleScanException
import com.polidea.rxandroidble3.scan.ScanSettings
import com.syncrow.R
import com.syncrow.data.CloudSyncManager
import com.syncrow.data.StravaRepository
import com.syncrow.data.db.*
import com.syncrow.hal.IHeartRateMonitor
import com.syncrow.hal.IRowingMachine
import com.syncrow.hal.RowerMetrics
import com.syncrow.hal.ble.BleHeartRateMonitor
import com.syncrow.hal.ble.FtmsRowingMachine
import com.syncrow.util.DataSmoother
import com.syncrow.util.PlanExchange
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.tasks.await

enum class SessionState {
  IDLE,
  COUNTDOWN,
  ROWING,
  PAUSED
}

enum class DeviceType {
  ROWER,
  HRM,
  UNKNOWN
}

data class DiscoveredDevice(
  val name: String,
  val address: String,
  val rssi: Int,
  val type: DeviceType
)

data class RuntimeSegment(
  val type: String, // Active, Recovery, etc.
  val durationType: String,
  val durationValue: Int,
  val targets: String, // formatted string like "20 SPM, 150W"
  val label: String // "Warm-up", "Interval 1/4", etc.
)

data class TrainingSessionState(
  val isActive: Boolean = false,
  val planName: String = "",
  val currentSegmentIndex: Int = 0,
  val totalSegments: Int = 0,
  val currentSegment: RuntimeSegment? = null,
  val nextSegment: RuntimeSegment? = null,
  val segmentTimeRemaining: Int = 0, // Seconds
  val segmentDistanceRemaining: Int = 0, // Meters
  val progress: Float = 0f
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
  private val splitDao: SplitDao,
  private val trainingDao: TrainingDao,
  private val stravaRepository: StravaRepository,
  private val cloudSyncManager: CloudSyncManager
) : AndroidViewModel(application) {

  private val prefs = application.getSharedPreferences("syncrow_prefs", Context.MODE_PRIVATE)
  private val KEY_LAST_USER_ID = "last_user_id"
  private val KEY_ROWER_PREFIX = "rower_addr_"
  private val KEY_HRM_PREFIX = "hrm_addr_"

  private val FTMS_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
  private val HRM_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

  private val rowingMachine: IRowingMachine = FtmsRowingMachine(rxBleClient)
  private val heartRateMonitor: IHeartRateMonitor = BleHeartRateMonitor(rxBleClient)
  private val planExchange = PlanExchange(application, trainingDao)

  private val powerSmoother = DataSmoother(windowSize = 3)
  private val paceSmoother = DataSmoother(windowSize = 3)
  private val spmSmoother = DataSmoother(windowSize = 3)
  private val heartRateSmoother = DataSmoother(windowSize = 3)

  private val _displayMetrics = MutableStateFlow(RowerMetrics(0, 0, 0, 0, 0))
  val displayMetrics: StateFlow<RowerMetrics> = _displayMetrics.asStateFlow()

  private val _sessionState = MutableStateFlow(SessionState.IDLE)
  val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

  private val _elapsedSeconds = MutableStateFlow(0)
  val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

  private val _countdownSeconds = MutableStateFlow(0)
  val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

  // Training State
  private val _trainingState = MutableStateFlow(TrainingSessionState())
  val trainingState: StateFlow<TrainingSessionState> = _trainingState.asStateFlow()

  private val _workoutFinishedEvent = MutableSharedFlow<Long?>()
  val workoutFinishedEvent = _workoutFinishedEvent.asSharedFlow()

  private var runtimeSegments: List<RuntimeSegment> = emptyList()
  private var segmentStartTime = 0
  private var segmentStartDistance = 0

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

  private val _isCloudPermanent = MutableStateFlow(false)
  val isCloudPermanent: StateFlow<Boolean> = _isCloudPermanent.asStateFlow()

  private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
  private var textToSpeech: TextToSpeech? = null
  private var ttsReady = false

  private var currentWorkoutId: Long? = null
  private var timerJob: Job? = null
  private var metricsJob: Job? = null
  private var hrJob: Job? = null
  private var recordingJob: Job? = null
  private var scanJob: Job? = null
  private var scanTimeoutJob: Job? = null
  private var countdownJob: Job? = null

  // Split tracking
  private var lastSplitTime = 0
  private var lastSplitDistance = 0
  private var splitCount = 0
  private var splitStartTimeMillis = 0L

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

    textToSpeech =
      TextToSpeech(application) { status ->
        if (status == TextToSpeech.SUCCESS) {
          val result = textToSpeech?.setLanguage(Locale.getDefault())
          ttsReady =
            result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
      }
  }

  override fun onCleared() {
    super.onCleared()
    toneGenerator.release()
    textToSpeech?.shutdown()
  }

  // --- Training Plan Logic ---

  fun prepareTraining(planId: Long) {
    viewModelScope.launch {
      val plan = trainingDao.getAllTrainingPlans().first().find { it.id == planId } ?: return@launch
      val blocks = trainingDao.getBlocksForPlanSync(planId)

      val segments = mutableListOf<RuntimeSegment>()

      blocks.forEach { block ->
        val blockSegments = trainingDao.getSegmentsForBlockSync(block.id)
        repeat(block.repeatCount) { round ->
          blockSegments.forEach { seg ->
            val label =
              if (block.repeatCount > 1) "${block.name} (${round + 1}/${block.repeatCount})"
              else block.name

            val targets = mutableListOf<String>()
            if (seg.targetSpm != null) targets.add("${seg.targetSpm} SPM")
            if (seg.targetWatts != null) targets.add("${seg.targetWatts}W")
            if (seg.targetPace != null) {
              val min = seg.targetPace / 60
              val sec = seg.targetPace % 60
              targets.add("%d:%02d".format(min, sec))
            }
            if (seg.targetHr != null) targets.add("HR ${seg.targetHr}")

            val targetStr = if (targets.isNotEmpty()) targets.joinToString(", ") else "Just Row"

            segments.add(
              RuntimeSegment(
                type = seg.segmentType,
                durationType = seg.durationType,
                durationValue = seg.durationValue,
                targets = targetStr,
                label = label
              )
            )
          }
        }
      }

      runtimeSegments = segments
      _trainingState.value =
        TrainingSessionState(
          isActive = true,
          planName = plan.name,
          totalSegments = segments.size,
          currentSegmentIndex = 0,
          currentSegment = segments.firstOrNull(),
          nextSegment = segments.getOrNull(1),
          segmentTimeRemaining =
            if (segments.firstOrNull()?.durationType == "TIME") segments.first().durationValue
            else 0,
          segmentDistanceRemaining =
            if (segments.firstOrNull()?.durationType == "DISTANCE") segments.first().durationValue
            else 0
        )
    }
  }

  fun clearTraining() {
    _trainingState.value = TrainingSessionState(isActive = false)
    runtimeSegments = emptyList()
  }

  private fun checkSegmentCompletion(currentSecs: Int, currentDist: Int) {
    val state = _trainingState.value
    if (!state.isActive || state.currentSegment == null) return

    val segment = state.currentSegment
    val idx = state.currentSegmentIndex

    // Calculate progress and remaining
    if (segment.durationType == "TIME") {
      val elapsedInSegment = currentSecs - segmentStartTime
      val remaining = segment.durationValue - elapsedInSegment

      // Audio countdown
      if (remaining in 1..5) {
        // Only beep once per second logic handled by update frequency (1s)
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
      } else if (remaining == 10 && ttsReady) {
        val next = runtimeSegments.getOrNull(idx + 1)
        if (next != null) {
          textToSpeech?.speak(
            "Next segment: ${next.type}, ${next.targets}",
            TextToSpeech.QUEUE_ADD,
            null,
            null
          )
        } else {
          textToSpeech?.speak("Almost done!", TextToSpeech.QUEUE_ADD, null, null)
        }
      }

      _trainingState.value =
        state.copy(
          segmentTimeRemaining = maxOf(0, remaining),
          progress = elapsedInSegment.toFloat() / segment.durationValue.toFloat()
        )

      if (remaining <= 0) {
        advanceSegment()
      }
    } else { // DISTANCE
      val distInSegment = currentDist - segmentStartDistance
      val remaining = segment.durationValue - distInSegment

      // Distance countdown audio is tricky without frequent updates,
      // but we check every 1s from timer, metrics might update faster.
      // Let's do a simple check
      if (remaining <= 50 && remaining > 0 && remaining % 10 == 0) { // e.g. 50m, 40m...
        // Maybe too chatty? Just beep at 20m?
      }

      _trainingState.value =
        state.copy(
          segmentDistanceRemaining = maxOf(0, remaining),
          progress = distInSegment.toFloat() / segment.durationValue.toFloat()
        )

      if (remaining <= 0) {
        advanceSegment()
      }
    }
  }

  fun skipSegment() {
    if (_trainingState.value.isActive) {
      advanceSegment()
    }
  }

  private fun advanceSegment() {
    // Auto-split first
    markSplit()

    val state = _trainingState.value
    val nextIndex = state.currentSegmentIndex + 1

    if (nextIndex < runtimeSegments.size) {
      val nextSeg = runtimeSegments[nextIndex]

      segmentStartTime = _elapsedSeconds.value
      segmentStartDistance = _displayMetrics.value.distance

      toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)

      _trainingState.value =
        state.copy(
          currentSegmentIndex = nextIndex,
          currentSegment = nextSeg,
          nextSegment = runtimeSegments.getOrNull(nextIndex + 1),
          segmentTimeRemaining = if (nextSeg.durationType == "TIME") nextSeg.durationValue else 0,
          segmentDistanceRemaining =
            if (nextSeg.durationType == "DISTANCE") nextSeg.durationValue else 0,
          progress = 0f
        )
    } else {
      // Training Complete
      toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
      if (ttsReady) {
        textToSpeech?.speak("Workout Complete!", TextToSpeech.QUEUE_FLUSH, null, null)
      }
      finishWorkout(true)
    }
  }

  // --- Existing Logic ---

  private fun loadUser() {
    viewModelScope.launch {
      val users = allUsers.first()

      if (users.isEmpty()) {
        val defaultId = userDao.insertUser(User(name = "Chris"))
        val user = userDao.getUserById(defaultId)
        setCurrentUserInternal(user)
      } else {
        val lastUserId = prefs.getLong(KEY_LAST_USER_ID, -1L)
        val user =
          if (lastUserId != -1L) {
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
    user?.let { u ->
      prefs.edit { putLong(KEY_LAST_USER_ID, u.id) }
      applyLanguage(u.languageCode)

      // Load saved devices for this user
      _selectedRowerAddress.value = prefs.getString("$KEY_ROWER_PREFIX${u.id}", null)
      _selectedHrmAddress.value = prefs.getString("$KEY_HRM_PREFIX${u.id}", null)

      // Trigger cloud sync check and automatic restore if enabled
      viewModelScope.launch {
        cloudSyncManager.updateSyncStatus(u)
        val auth = FirebaseAuth.getInstance()
        _isCloudPermanent.value = auth.currentUser != null && !auth.currentUser!!.isAnonymous

        if (u.cloudSyncEnabled) {
          restoreFromCloud(u)
        }
      }
    }
  }

  private fun applyLanguage(languageCode: String) {
    val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
    AppCompatDelegate.setApplicationLocales(appLocale)
  }

  fun addUser(name: String) {
    viewModelScope.launch { userDao.insertUser(User(name = name)) }
  }

  fun updateCurrentUser(user: User) {
    val oldSyncEnabled = _currentUser.value?.cloudSyncEnabled ?: false
    viewModelScope.launch {
      userDao.updateUser(user.copy(lastUpdated = System.currentTimeMillis()))
      setCurrentUserInternal(userDao.getUserById(user.id))

      // If sync was just enabled, trigger a restore
      if (user.cloudSyncEnabled && !oldSyncEnabled) {
        restoreFromCloud(user)
      }
    }
  }

  private suspend fun restoreFromCloud(user: User) {
    _toastEvent.emit(ToastEvent.String("Checking for cloud data..."))
    val success = cloudSyncManager.pullAndRestoreData(user)
    if (success) {
      _toastEvent.emit(ToastEvent.String("Cloud data restored successfully!"))
      // Reload current user in case the profile was updated
      _currentUser.value = userDao.getUserById(user.id)
    }
  }

  fun linkWithGoogle(idToken: String) {
    val user = _currentUser.value ?: return
    viewModelScope.launch {
      try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser

        val authResultUser =
          if (currentUser != null && currentUser.isAnonymous) {
            try {
              currentUser.linkWithCredential(credential).await().user
            } catch (e: FirebaseAuthUserCollisionException) {
              // Account already exists with this Google credential.
              // Sign in to it instead to "import" that cloud data.
              firebaseAuth.signInWithCredential(credential).await().user
            }
          } else {
            firebaseAuth.signInWithCredential(credential).await().user
          }

        val newUid = authResultUser?.uid
        if (newUid != null) {
          val updatedUser =
            user.copy(firebaseUid = newUid, lastUpdated = System.currentTimeMillis())
          userDao.updateUser(updatedUser)
          _currentUser.value = updatedUser
          _isCloudPermanent.value = true
          _toastEvent.emit(ToastEvent.String("Account synced with Google!"))
          // Trigger a full sync/restore after linking
          restoreFromCloud(updatedUser)
          cloudSyncManager.updateSyncStatus(updatedUser)
        }
      } catch (e: Exception) {
        Log.e("WorkoutViewModel", "Linking failed", e)
        _toastEvent.emit(ToastEvent.String("Failed to link account: ${e.message}"))
      }
    }
  }

  fun switchUser(user: User) {
    setCurrentUserInternal(user)
    // No need to clear addresses manually; setCurrentUserInternal loads the new user's saved ones
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getWorkoutsForCurrentUser(): Flow<List<Workout>> {
    return currentUser.flatMapLatest { user ->
      user?.id?.let { workoutDao.getWorkoutsForUser(it) } ?: emptyFlow()
    }
  }

  @Suppress("unused")
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

  @OptIn(FlowPreview::class)
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

    scanJob =
      viewModelScope.launch {
        rxBleClient
          .scanBleDevices(
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
          )
          .asFlow()
          .catch { e ->
            Log.e("WorkoutViewModel", "Scan error: ${e.message}")
            _isScanning.value = false
            if (e is BleScanException && e.reason == BleScanException.BLUETOOTH_DISABLED) {
              _toastEvent.emit(ToastEvent.Resource(R.string.error_bluetooth_disabled))
            } else {
              _toastEvent.emit(
                ToastEvent.Resource(
                  R.string.error_rower_connection_failed,
                  listOf(e.message ?: "Unknown")
                )
              )
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
            var type =
              when {
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

            val finalName =
              if (scanName == "Unknown Device" && existingIndex != -1) {
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
    scanTimeoutJob =
      viewModelScope.launch {
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
        prefs.edit { putString("$KEY_ROWER_PREFIX${user.id}", device.address) }
      }
      DeviceType.HRM -> {
        _selectedHrmAddress.value = device.address
        prefs.edit { putString("$KEY_HRM_PREFIX${user.id}", device.address) }
      }
      else -> {}
    }
  }

  fun startWorkout() {
    if (_sessionState.value == SessionState.ROWING || _sessionState.value == SessionState.COUNTDOWN)
      return
    val user = _currentUser.value ?: return

    if (selectedRowerAddress.value == null) {
      viewModelScope.launch {
        _toastEvent.emit(ToastEvent.Resource(R.string.error_no_rower_selected))
      }
      return
    }

    viewModelScope.launch {
      if (_sessionState.value == SessionState.IDLE) {
        val now = System.currentTimeMillis()
        currentWorkoutId = workoutDao.insertWorkout(Workout(userId = user.id, startTime = now))
        // Reset split counters
        lastSplitTime = 0
        lastSplitDistance = 0
        splitCount = 0
        splitStartTimeMillis = now

        // Reset training progress if active
        if (_trainingState.value.isActive) {
          segmentStartTime = 0
          segmentStartDistance = 0
        }

        startCountdown()
      } else if (_sessionState.value == SessionState.PAUSED) {
        // Resume immediately from pause, no countdown
        _sessionState.value = SessionState.ROWING
        startTimer()
        startMetricsCollection()
      }
    }
  }

  fun markSplit() {
    // Also allow split in PAUSED state if we are finishing?
    // Usually splits happen during rowing.
    // But if we call this from finishWorkout, state might be PAUSED.
    if (_sessionState.value != SessionState.ROWING && _sessionState.value != SessionState.PAUSED)
      return

    val workoutId = currentWorkoutId ?: return

    splitCount++
    val currentSeconds = _elapsedSeconds.value
    val currentDist = _displayMetrics.value.distance
    val now = System.currentTimeMillis()

    val splitSeconds = currentSeconds - lastSplitTime
    val splitDist = currentDist - lastSplitDistance

    // Safety check for negative split distance
    if (splitDist < 0) {
      Log.w("WorkoutViewModel", "Negative split distance detected: $splitDist. Ignoring split.")
      return
    }

    viewModelScope.launch {
      recordSplitInternal(
        workoutId,
        splitCount,
        splitStartTimeMillis,
        now,
        splitDist,
        splitSeconds,
        true
      )
    }

    lastSplitTime = currentSeconds
    lastSplitDistance = currentDist
    splitStartTimeMillis = now
  }

  private suspend fun recordSplitInternal(
    workoutId: Long,
    index: Int,
    start: Long,
    end: Long,
    dist: Int,
    secs: Int,
    showToast: Boolean
  ) {
    // Fetch average metrics for this split interval
    val points = metricPointDao.getPointsInRange(workoutId, start, end)

    val avgPace = if (points.isNotEmpty()) points.map { it.pace }.average().toInt() else 0
    val avgPower = if (points.isNotEmpty()) points.map { it.power }.average().toInt() else 0
    val avgHR =
      if (points.isNotEmpty()) points.map { it.heartRate }.filter { it > 0 }.average().toInt()
      else 0
    val avgStroke = if (points.isNotEmpty()) points.map { it.strokeRate }.average().toInt() else 0

    val split =
      WorkoutSplit(
        workoutId = workoutId,
        splitIndex = index,
        startTime = start,
        endTime = end,
        distanceMeters = dist,
        durationSeconds = secs,
        avgPace = avgPace,
        avgPower = avgPower,
        avgHeartRate = avgHR,
        avgStrokeRate = avgStroke
      )

    splitDao.insertSplit(split)

    if (showToast) {
      // UI Feedback
      val min = secs / 60
      val sec = secs % 60
      val timeStr = "%d:%02d".format(min, sec)

      // Format pace
      val paceMin = avgPace / 60
      val paceSec = avgPace % 60
      val paceStr = "%d:%02d".format(paceMin, paceSec)

      _toastEvent.emit(ToastEvent.String("Split $index: $dist m in $timeStr ($paceStr/500m)"))
    }
  }

  private fun startCountdown() {
    countdownJob?.cancel()
    _sessionState.value = SessionState.COUNTDOWN

    countdownJob =
      viewModelScope.launch {
        for (i in 3 downTo 1) {
          _countdownSeconds.value = i
          toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
          delay(1000)
        }
        _countdownSeconds.value = 0
        // GO sound
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)

        if (ttsReady && _trainingState.value.isActive) {
          val seg = _trainingState.value.currentSegment
          if (seg != null) {
            textToSpeech?.speak("${seg.type}, ${seg.targets}", TextToSpeech.QUEUE_ADD, null, null)
          }
        }

        _sessionState.value = SessionState.ROWING
        startTimer()
        startMetricsCollection()
        startRecording()

        // Reset start time for first split to match actual GO time
        splitStartTimeMillis = System.currentTimeMillis()
      }
  }

  fun pauseWorkout() {
    _sessionState.value = SessionState.PAUSED
    stopWorkoutJobs()
  }

  fun finishWorkout(save: Boolean) {
    val workoutId = currentWorkoutId
    viewModelScope.launch {
      stopWorkoutJobs()

      if (workoutId != null) {
        if (save) {
          // AUTO-SPLIT: Record the final segment if there is remaining distance/time
          val currentSeconds = _elapsedSeconds.value
          val currentDist = _displayMetrics.value.distance
          val splitSeconds = currentSeconds - lastSplitTime
          val splitDist = currentDist - lastSplitDistance

          if (splitSeconds > 0 || splitDist > 0) {
            splitCount++
            recordSplitInternal(
              workoutId,
              splitCount,
              splitStartTimeMillis,
              System.currentTimeMillis(),
              if (splitDist >= 0) splitDist else 0, // Prevent negative
              splitSeconds,
              false // No toast for auto-split
            )
          }

          val workout = workoutDao.getWorkoutById(workoutId)
          if (workout != null) {
            val metrics = _displayMetrics.value
            workout.endTime = System.currentTimeMillis()
            workout.totalDistanceMeters = metrics.distance
            workout.totalSeconds = _elapsedSeconds.value

            val points = metricPointDao.getPointsForWorkout(workoutId).first()
            if (points.isNotEmpty()) {
              workout.avgPower = points.map { it.power }.average().toInt()
              workout.avgHeartRate =
                points.filter { it.heartRate > 0 }.map { it.heartRate }.average().toInt()
            } else {
              workout.avgPower = metrics.power
              workout.avgHeartRate = metrics.heartRate
            }

            workoutDao.updateWorkout(workout)
            Log.d(
              "WorkoutViewModel",
              "Workout saved: ID $workoutId, Dist ${workout.totalDistanceMeters}m"
            )

            // Auto-upload
            val currentUser = _currentUser.value
            if (
              currentUser != null && currentUser.cloudSyncEnabled && currentUser.stravaToken != null
            ) {
              syncWorkoutToStrava(workoutId)
            }

            _workoutFinishedEvent.emit(workoutId)
          }
        } else {
          val workout = workoutDao.getWorkoutById(workoutId)
          if (workout != null) {
            workoutDao.deleteWorkout(workout)
            Log.d("WorkoutViewModel", "Workout deleted: ID $workoutId")
          }
          _workoutFinishedEvent.emit(null)
        }
      } else {
        _workoutFinishedEvent.emit(null)
      }

      _sessionState.value = SessionState.IDLE
      _elapsedSeconds.value = 0
      _displayMetrics.value = RowerMetrics(0, 0, 0, 0, _displayMetrics.value.heartRate)
      currentWorkoutId = null
      clearTraining()
    }
  }

  private fun stopWorkoutJobs() {
    timerJob?.cancel()
    metricsJob?.cancel()
    recordingJob?.cancel()
    countdownJob?.cancel()
  }

  private fun startTimer() {
    timerJob?.cancel()
    timerJob =
      viewModelScope.launch {
        while (true) {
          delay(1000)
          _elapsedSeconds.value += 1
          checkSegmentCompletion(_elapsedSeconds.value, _displayMetrics.value.distance)
        }
      }
  }

  private fun startMetricsCollection() {
    metricsJob?.cancel()
    metricsJob =
      viewModelScope.launch {
        rowingMachine
          .getMetricsFlow(_selectedRowerAddress.value)
          .conflate() // Ensure we only process the latest data and don't backlog
          .catch { e ->
            Log.e("WorkoutViewModel", "Metrics collection error: ${e.message}")
            _toastEvent.emit(
              ToastEvent.Resource(
                R.string.error_rower_connection_failed,
                listOf(e.message ?: "Unknown")
              )
            )
          }
          .collect { rawData ->
            val smoothedPower = powerSmoother.add(rawData.power.toDouble()).toInt()
            val smoothedPace = paceSmoother.add(rawData.pace.toDouble()).toInt()
            val smoothedSpm = spmSmoother.add(rawData.strokeRate.toDouble()).toInt()
            _displayMetrics.value =
              _displayMetrics.value.copy(
                power = smoothedPower,
                pace = smoothedPace,
                strokeRate = smoothedSpm,
                distance = rawData.distance
              )
          }
      }
  }

  private fun startHeartRateCollection(address: String? = null) {
    hrJob?.cancel()
    hrJob =
      viewModelScope.launch {
        heartRateMonitor
          .getHeartRateFlow(address ?: _selectedHrmAddress.value)
          .conflate()
          .catch { e -> Log.e("WorkoutViewModel", "HR collection error: ${e.message}") }
          .collect { bpm ->
            val finalBpm =
              if (bpm > 0) {
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
    recordingJob =
      viewModelScope.launch(Dispatchers.IO) {
        while (true) {
          delay(1000)
          val workoutId = currentWorkoutId ?: break
          val m = _displayMetrics.value
          metricPointDao.insertMetricPoint(
            MetricPoint(
              workoutId = workoutId,
              timestamp = System.currentTimeMillis(),
              power = m.power,
              pace = m.pace,
              strokeRate = m.strokeRate,
              distance = m.distance,
              heartRate = m.heartRate
            )
          )
        }
      }
  }

  fun getMetricPointsForWorkout(workoutId: Long): Flow<List<MetricPoint>> {
    return metricPointDao.getPointsForWorkout(workoutId)
  }

  fun getSplitsForWorkout(workoutId: Long): Flow<List<WorkoutSplit>> {
    return splitDao.getSplitsForWorkout(workoutId)
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
      val updatedUser =
        user.copy(stravaToken = null, stravaRefreshToken = null, stravaTokenExpiresAt = null)
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
      // Fetch splits here if needed for Strava
      val splits = splitDao.getSplitsForWorkoutSync(workoutId)

      _toastEvent.emit(ToastEvent.Resource(R.string.strava_syncing))
      // TODO: Update uploadWorkout to accept splits
      val success = stravaRepository.uploadWorkout(workout, points, splits, user)
      if (success) {
        _toastEvent.emit(ToastEvent.Resource(R.string.strava_sync_success))
      } else {
        _toastEvent.emit(ToastEvent.Resource(R.string.strava_sync_error))
      }
    }
  }

  fun importTrainingPlanFromUri(uri: Uri) {
    viewModelScope.launch {
      val newPlanId = planExchange.importPlan(uri)
      if (newPlanId != null) {
        _toastEvent.emit(ToastEvent.String("Training plan imported successfully!"))
      } else {
        _toastEvent.emit(
          ToastEvent.String("Failed to import training plan. Please check the file format.")
        )
      }
    }
  }

  class Factory(
    private val application: Application,
    private val rxBleClient: RxBleClient,
    private val userDao: UserDao,
    private val workoutDao: WorkoutDao,
    private val metricPointDao: MetricPointDao,
    private val splitDao: SplitDao,
    private val trainingDao: TrainingDao,
    private val stravaRepository: StravaRepository,
    private val cloudSyncManager: CloudSyncManager
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      WorkoutViewModel(
        application,
        rxBleClient,
        userDao,
        workoutDao,
        metricPointDao,
        splitDao,
        trainingDao,
        stravaRepository,
        cloudSyncManager
      )
        as T
  }
}
