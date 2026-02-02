package com.syncrow.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.syncrow.R
import com.syncrow.data.db.Workout
import com.syncrow.ui.workout.WorkoutViewModel
import com.syncrow.util.TcxExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(viewModel: WorkoutViewModel, workoutId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workouts by viewModel.getWorkoutsForCurrentUser().collectAsState(initial = emptyList())
    val workout = workouts.find { it.id == workoutId }
    
    var showDeleteDialog by remember { mutableStateOf(false) }

    val onExport = {
        scope.launch {
            val w = workout
            if (w != null) {
                val points = viewModel.getMetricPointsForWorkout(workoutId).first()
                TcxExporter(context).exportWorkout(w, points)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_workout_title)) },
            text = { Text(stringResource(R.string.dialog_delete_workout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkout(workoutId)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_workout_summary)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onExport() }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_export_tcx))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dialog_delete_workout_title))
                    }
                }
            )
        }
    ) { padding ->
        workout?.let { w ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val dateFormat = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                
                Text(text = dateFormat.format(Date(w.startTime)), style = MaterialTheme.typography.headlineSmall)
                Text(text = stringResource(R.string.label_started_at, timeFormat.format(Date(w.startTime))), color = Color.Gray)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DetailMetric(stringResource(R.string.label_distance), "${w.totalDistanceMeters}m")
                    DetailMetric(stringResource(R.string.label_time), formatTime(w.totalSeconds))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DetailMetric(stringResource(R.string.label_avg_power), "${w.avgPower}W")
                    DetailMetric(stringResource(R.string.label_avg_hr), "${w.avgHeartRate} bpm")
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Button(
                    onClick = { onExport() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_export_tcx))
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun DetailMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
