package com.syncrow.ui.workout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
            // onFinish is now handled by the LaunchedEffect
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
              // onFinish is now handled by the LaunchedEffect
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
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        trainingState.planName,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
      )
      Text(
        "Segment ${trainingState.currentSegmentIndex + 1}/${trainingState.totalSegments}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Main Content Area
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Segment Label & Targets
        Text(
          text = trainingState.currentSegment?.label ?: "",
          color = MaterialTheme.colorScheme.onBackground,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = trainingState.currentSegment?.targets ?: "",
          color = MaterialTheme.colorScheme.secondary,
          fontSize = 32.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Countdown
        if (trainingState.currentSegment?.durationType == "TIME") {
          Text(
            text = formatTime(trainingState.segmentTimeRemaining),
            color =
              if (trainingState.segmentTimeRemaining <= 5) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary,
            fontSize = 100.sp,
            fontWeight = FontWeight.Black
          )
          Text("REMAINING", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        } else {
          Text(
            text = "${trainingState.segmentDistanceRemaining}m",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 80.sp,
            fontWeight = FontWeight.Black
          )
          Text("REMAINING", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        }
      }
    }

    // Next Segment Preview
    if (trainingState.nextSegment != null) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
      ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(
            "NEXT:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
          )
          Column {
            Text(
              text = trainingState.nextSegment.label,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = trainingState.nextSegment.targets,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp
            )
          }
        }
      }
    }

    // Bottom Metrics Row (Mini Dashboard)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
      MetricDisplay(label = "TIME", value = formatTime(elapsedSeconds), valueSize = 24.sp)
      MetricDisplay(label = "DIST", value = "${metrics.distance}", valueSize = 24.sp)
      MetricDisplay(label = "SPM", value = "${metrics.strokeRate}", valueSize = 24.sp)
      MetricDisplay(label = "WATTS", value = "${metrics.power}", valueSize = 24.sp)
      MetricDisplay(
        label = "HR",
        value = "${metrics.heartRate}",
        color = Color.Red,
        valueSize = 24.sp
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SessionControls(sessionState, viewModel, onStop)
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
      color = Color(0xFF00C853), // Keep green for brand/status
      fontSize = 16.sp,
      fontWeight = FontWeight.ExtraBold
    )
    Text(
      text = formatTime(elapsedSeconds),
      color = MaterialTheme.colorScheme.primary, // Used to be Yellow
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

  Row(
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
  ) {
    when (sessionState) {
      SessionState.IDLE -> {
        Button(onClick = { viewModel.startWorkout() }) { Text(stringResource(R.string.btn_start)) }
      }
      SessionState.COUNTDOWN -> {
        // Controls hidden during countdown
      }
      SessionState.ROWING -> {
        Button(onClick = { viewModel.pauseWorkout() }) { Text(stringResource(R.string.btn_pause)) }
        Spacer(modifier = Modifier.width(8.dp))

        if (trainingState.isActive) {
          OutlinedButton(
            onClick = { viewModel.skipSegment() },
            colors =
              ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
          ) {
            Text("SKIP") // TODO: Localize
          }
        } else {
          OutlinedButton(
            onClick = { viewModel.markSplit() },
            colors =
              ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
          ) {
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
  isPrimary: Boolean = false,
  color: Color = MaterialTheme.colorScheme.onBackground, // Default to OnBackground
  valueSize: TextUnit? = null,
  labelSize: TextUnit? = null
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
    Text(
      text = label,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = labelSize ?: if (isPrimary) 14.sp else 12.sp,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = value,
      color = color,
      fontSize = valueSize ?: if (isPrimary) 60.sp else 30.sp,
      fontWeight = FontWeight.Black
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
