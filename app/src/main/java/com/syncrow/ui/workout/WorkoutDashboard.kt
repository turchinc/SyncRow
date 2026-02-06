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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncrow.R
import com.syncrow.hal.RowerMetrics

@Composable
fun WorkoutDashboard(viewModel: WorkoutViewModel, onFinish: () -> Unit) {
  val metrics by viewModel.displayMetrics.collectAsState()
  val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
  val sessionState by viewModel.sessionState.collectAsState()
  val countdownSeconds by viewModel.countdownSeconds.collectAsState()
  val configuration = LocalConfiguration.current
  var showSaveDialog by remember { mutableStateOf(false) }

  // Keep screen on during workout
  KeepScreenOn()

  Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF001220)) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        LandscapeLayout(metrics, elapsedSeconds, sessionState, viewModel) {
          viewModel.pauseWorkout() // Pause immediately on STOP
          showSaveDialog = true
        }
      } else {
        PortraitLayout(metrics, elapsedSeconds, sessionState, viewModel) {
          viewModel.pauseWorkout() // Pause immediately on STOP
          showSaveDialog = true
        }
      }

      // Countdown Overlay
      if (sessionState == SessionState.COUNTDOWN) {
        Box(
          modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (countdownSeconds > 0) countdownSeconds.toString() else "GO!",
            color = if (countdownSeconds > 0) Color.Yellow else Color.Green,
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
            onFinish()
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
              onFinish()
            }
          ) {
            Text(stringResource(R.string.btn_discard), color = Color.Red)
          }
        }
      }
    )
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
      color = Color.Green,
      fontSize = 16.sp,
      fontWeight = FontWeight.ExtraBold
    )
    Text(
      text = formatTime(elapsedSeconds),
      color = Color.Yellow,
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
        color = Color.Yellow,
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
  Row(
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(top = 8.dp)
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
        // NEW: Split Button
        OutlinedButton(
          onClick = { viewModel.markSplit() },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
          Text(stringResource(R.string.btn_split))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
          Text(stringResource(R.string.btn_stop))
        }
      }
      SessionState.PAUSED -> {
        Button(onClick = { viewModel.startWorkout() }) { Text(stringResource(R.string.btn_resume)) }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
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
  color: Color = Color.White,
  valueSize: TextUnit? = null,
  labelSize: TextUnit? = null
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
    Text(
      text = label,
      color = Color.Gray,
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
