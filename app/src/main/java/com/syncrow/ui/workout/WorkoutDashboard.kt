package com.syncrow.ui.workout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncrow.R
import com.syncrow.hal.RowerMetrics

@Composable
fun WorkoutDashboard(viewModel: WorkoutViewModel, onFinish: (Long?) -> Unit) {
  val metrics by viewModel.displayMetrics.collectAsState()
  val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
  val sessionState by viewModel.sessionState.collectAsState()
  val countdownSeconds by viewModel.countdownSeconds.collectAsState()
  val trainingState by viewModel.trainingState.collectAsState()

  val configuration = LocalConfiguration.current
  var showSaveDialog by remember { mutableStateOf(false) }

  // Keep screen on during workout
  KeepScreenOn()

  LaunchedEffect(Unit) {
    viewModel.workoutFinishedEvent.collect { savedWorkoutId -> onFinish(savedWorkoutId) }
  }

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (trainingState.isActive) {
        TrainingLayout(metrics, elapsedSeconds, sessionState, trainingState, viewModel) {
          viewModel.pauseWorkout()
          showSaveDialog = true
        }
      } else {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
          LandscapeLayout(metrics, elapsedSeconds, sessionState, viewModel) {
            viewModel.pauseWorkout()
            showSaveDialog = true
          }
        } else {
          PortraitLayout(metrics, elapsedSeconds, sessionState, viewModel) {
            viewModel.pauseWorkout()
            showSaveDialog = true
          }
        }
      }

      // Countdown Overlay
      if (sessionState == SessionState.COUNTDOWN) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (countdownSeconds > 0) countdownSeconds.toString() else "GO!",
            color =
              if (countdownSeconds > 0) MaterialTheme.colorScheme.primary
              else Color(0xFF00C853), // Green
            fontSize = 120.sp,
            fontWeight = FontWeight.Black
          )
        }
      }
    }
  }

  if (showSaveDialog) {
    AlertDialog(
      onDismissRequest = { /* Don't dismiss without action */},
      title = { Text(stringResource(R.string.dialog_end_workout_title)) },
      text = { Text(stringResource(R.string.dialog_end_workout_message)) },
      confirmButton = {
        Button(
          onClick = {
            viewModel.finishWorkout(save = true)
            showSaveDialog = false
          }
        ) {
          Text(stringResource(R.string.btn_save))
        }
      },
      dismissButton = {
        Row {
          // Resume Button
          TextButton(
            onClick = {
              viewModel.startWorkout() // Resume timer
              showSaveDialog = false
            }
          ) {
            Text(stringResource(R.string.btn_resume))
          }
          Spacer(modifier = Modifier.width(8.dp))
          // Discard Button
          TextButton(
            onClick = {
              viewModel.finishWorkout(save = false)
              showSaveDialog = false
            }
          ) {
            Text(stringResource(R.string.btn_discard), color = MaterialTheme.colorScheme.error)
          }
        }
      }
    )
  }
}

@Composable
fun TrainingLayout(
  metrics: RowerMetrics,
  elapsedSeconds: Int,
  sessionState: SessionState,
  trainingState: TrainingSessionState,
  viewModel: WorkoutViewModel,
  onStop: () -> Unit
) {
  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
    // 1. Header Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = trainingState.planName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      Text(
        text = "Segment ${trainingState.currentSegmentIndex + 1}/${trainingState.totalSegments}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // 2. Flexible Content Area
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      if (isLandscape) {
        // Landscape: Side-by-Side to prevent crushing vertical space
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
          // Left Side: Current Segment Info & Large Timer
          Column(
            modifier = Modifier.weight(1.1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = trainingState.currentSegment?.label ?: "",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            )
            Text(
              text = trainingState.currentSegment?.targets ?: "",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.secondary,
              fontWeight = FontWeight.Black,
              textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            TrainingTimer(trainingState, fontSize = 60.sp)
          }

          // Right Side: Dashboard Metrics Grid
          Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            MetricsGrid(
              metrics = metrics,
              elapsedSeconds = elapsedSeconds,
              valueSize = 32.sp,
              labelSize = 12.sp,
              spacing = 4.dp
            )
          }
        }
      } else {
        // Portrait: Stacked view
        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.SpaceEvenly
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = trainingState.currentSegment?.label ?: "",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            )
            Text(
              text = trainingState.currentSegment?.targets ?: "",
              style = MaterialTheme.typography.headlineLarge,
              color = MaterialTheme.colorScheme.secondary,
              fontWeight = FontWeight.Black,
              textAlign = TextAlign.Center
            )
          }

          TrainingTimer(trainingState, fontSize = 90.sp)

          MetricsGrid(
            metrics = metrics,
            elapsedSeconds = elapsedSeconds,
            valueSize = 42.sp,
            labelSize = 14.sp,
            spacing = 12.dp
          )
        }
      }
    }

    // 3. Footer Area (Safe from overlap)
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Next Preview
      Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        if (trainingState.nextSegment != null) {
          Column {
            Text(
              text = "NEXT: ${trainingState.nextSegment.label}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = trainingState.nextSegment.targets,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
      // Action Buttons
      SessionControls(sessionState, viewModel, onStop)
    }
  }
}

@Composable
fun TrainingTimer(trainingState: TrainingSessionState, fontSize: TextUnit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    val isTime = trainingState.currentSegment?.durationType == "TIME"
    val remainingValue =
      if (isTime) formatTime(trainingState.segmentTimeRemaining)
      else "${trainingState.segmentDistanceRemaining}m"

    Text(
      text = remainingValue,
      style = MaterialTheme.typography.displayLarge,
      fontSize = fontSize,
      fontWeight = FontWeight.Black,
      color =
        if (isTime && trainingState.segmentTimeRemaining <= 5) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary
    )
    Text(
      text = "REMAINING",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
fun MetricsGrid(
  metrics: RowerMetrics,
  elapsedSeconds: Int,
  valueSize: TextUnit,
  labelSize: TextUnit,
  spacing: androidx.compose.ui.unit.Dp
) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
    Row(modifier = Modifier.fillMaxWidth()) {
      MetricDisplay(
        "TIME",
        formatTime(elapsedSeconds),
        modifier = Modifier.weight(1f),
        valueSize = valueSize,
        labelSize = labelSize
      )
      MetricDisplay(
        "DIST",
        "${metrics.distance}",
        modifier = Modifier.weight(1f),
        valueSize = valueSize,
        labelSize = labelSize
      )
    }
    Row(modifier = Modifier.fillMaxWidth()) {
      MetricDisplay(
        "/500m",
        formatPace(metrics.pace),
        modifier = Modifier.weight(1f),
        valueSize = valueSize,
        labelSize = labelSize
      )
      MetricDisplay(
        "SPM",
        "${metrics.strokeRate}",
        modifier = Modifier.weight(1f),
        valueSize = valueSize,
        labelSize = labelSize
      )
    }
    Row(modifier = Modifier.fillMaxWidth()) {
      MetricDisplay(
        "WATTS",
        "${metrics.power}",
        modifier = Modifier.weight(1f),
        valueSize = valueSize,
        labelSize = labelSize
      )
      MetricDisplay(
        "HR",
        "${metrics.heartRate}",
        modifier = Modifier.weight(1f),
        color = Color.Red,
        valueSize = valueSize,
        labelSize = labelSize
      )
    }
  }
}

@Composable
fun KeepScreenOn() {
  val context = LocalContext.current
  DisposableEffect(Unit) {
    val window = context.findActivity()?.window
    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
  }
}

private fun Context.findActivity(): Activity? {
  var context = this
  while (context is ContextWrapper) {
    if (context is Activity) return context
    context = context.baseContext
  }
  return null
}

@Composable
fun PortraitLayout(
  metrics: RowerMetrics,
  elapsedSeconds: Int,
  sessionState: SessionState,
  viewModel: WorkoutViewModel,
  onStop: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.SpaceEvenly,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(R.string.btn_just_row),
      color = Color(0xFF00C853),
      fontSize = 16.sp,
      fontWeight = FontWeight.ExtraBold
    )
    Text(
      text = formatTime(elapsedSeconds),
      color = MaterialTheme.colorScheme.primary,
      fontSize = 48.sp,
      fontWeight = FontWeight.Bold
    )
    MetricDisplay(
      label = stringResource(R.string.label_pace),
      value = formatPace(metrics.pace),
      isPrimary = true
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      MetricDisplay(
        label = stringResource(R.string.label_power_watts),
        value = metrics.power.toString()
      )
      MetricDisplay(
        label = stringResource(R.string.label_stroke_rate),
        value = metrics.strokeRate.toString()
      )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      MetricDisplay(
        label = stringResource(R.string.label_distance_meters),
        value = metrics.distance.toString()
      )
      MetricDisplay(
        label = stringResource(R.string.label_heart_rate),
        value = if (metrics.heartRate > 0) metrics.heartRate.toString() else "--",
        color = Color.Red
      )
    }
    SessionControls(sessionState, viewModel, onStop)
  }
}

@Composable
fun LandscapeLayout(
  metrics: RowerMetrics,
  elapsedSeconds: Int,
  sessionState: SessionState,
  viewModel: WorkoutViewModel,
  onStop: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxSize().padding(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(
      modifier = Modifier.weight(1f),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = formatTime(elapsedSeconds),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 60.sp,
        fontWeight = FontWeight.Bold
      )
      MetricDisplay(
        label = stringResource(R.string.label_pace),
        value = formatPace(metrics.pace),
        isPrimary = true,
        valueSize = 80.sp
      )
      SessionControls(sessionState, viewModel, onStop)
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.SpaceEvenly,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        MetricDisplay(
          label = stringResource(R.string.label_power),
          value = metrics.power.toString(),
          valueSize = 56.sp,
          labelSize = 20.sp
        )
        MetricDisplay(
          label = stringResource(R.string.label_stroke),
          value = metrics.strokeRate.toString(),
          valueSize = 56.sp,
          labelSize = 20.sp
        )
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        MetricDisplay(
          label = stringResource(R.string.label_dist),
          value = metrics.distance.toString(),
          valueSize = 56.sp,
          labelSize = 20.sp
        )
        MetricDisplay(
          label = stringResource(R.string.label_hr),
          value = if (metrics.heartRate > 0) metrics.heartRate.toString() else "--",
          color = Color.Red,
          valueSize = 56.sp,
          labelSize = 20.sp
        )
      }
    }
  }
}

@Composable
fun SessionControls(sessionState: SessionState, viewModel: WorkoutViewModel, onStop: () -> Unit) {
  val trainingState by viewModel.trainingState.collectAsState()

  Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
    when (sessionState) {
      SessionState.IDLE -> {
        Button(
          onClick = { viewModel.startWorkout() },
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
          Text(stringResource(R.string.btn_start))
        }
      }
      SessionState.COUNTDOWN -> {}
      SessionState.ROWING -> {
        Button(onClick = { viewModel.pauseWorkout() }) { Text(stringResource(R.string.btn_pause)) }
        Spacer(modifier = Modifier.width(8.dp))
        if (trainingState.isActive) {
          OutlinedButton(onClick = { viewModel.skipSegment() }) {
            Text(stringResource(R.string.btn_skip))
          }
        } else {
          OutlinedButton(onClick = { viewModel.markSplit() }) {
            Text(stringResource(R.string.btn_split))
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = onStop,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text(stringResource(R.string.btn_stop))
        }
      }
      SessionState.PAUSED -> {
        Button(onClick = { viewModel.startWorkout() }) { Text(stringResource(R.string.btn_resume)) }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = onStop,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text(stringResource(R.string.btn_stop))
        }
      }
    }
  }
}

@Composable
fun MetricDisplay(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  isPrimary: Boolean = false,
  color: Color = MaterialTheme.colorScheme.onBackground,
  valueSize: TextUnit? = null,
  labelSize: TextUnit? = null
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(2.dp)) {
    Text(
      text = label,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = labelSize ?: if (isPrimary) 14.sp else 12.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1
    )
    Text(
      text = value,
      color = color,
      fontSize = valueSize ?: if (isPrimary) 60.sp else 30.sp,
      fontWeight = FontWeight.Black,
      maxLines = 1
    )
  }
}

private fun formatPace(totalSeconds: Int): String {
  if (totalSeconds == 0) return "0:00"
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}

private fun formatTime(totalSeconds: Int): String {
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%02d:%02d".format(minutes, seconds)
}
