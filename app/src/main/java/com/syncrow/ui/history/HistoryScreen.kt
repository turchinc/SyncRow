package com.syncrow.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.syncrow.R
import com.syncrow.data.db.Workout
import com.syncrow.ui.workout.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: WorkoutViewModel, onBack: () -> Unit, onNavigateToDetail: (Long) -> Unit) {
    val workouts by viewModel.getWorkoutsForCurrentUser().collectAsState(initial = emptyList())
    var selectedWorkouts by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedWorkouts.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selection_count, selectedWorkouts.size))
                    } else {
                        Text(stringResource(R.string.btn_history))
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedWorkouts = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_cancel_selection))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            viewModel.deleteWorkouts(selectedWorkouts.toList())
                            selectedWorkouts = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_selected))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (workouts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.label_no_workouts), color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(workouts) { workout ->
                        WorkoutHistoryItem(
                            workout = workout,
                            isSelected = selectedWorkouts.contains(workout.id),
                            onToggleSelection = {
                                selectedWorkouts = if (selectedWorkouts.contains(workout.id)) {
                                    selectedWorkouts - workout.id
                                } else {
                                    selectedWorkouts + workout.id
                                }
                            },
                            onNavigate = {
                                if (isSelectionMode) {
                                    selectedWorkouts = if (selectedWorkouts.contains(workout.id)) {
                                        selectedWorkouts - workout.id
                                    } else {
                                        selectedWorkouts + workout.id
                                    }
                                } else {
                                    onNavigateToDetail(workout.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutHistoryItem(
    workout: Workout,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onNavigate: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(workout.startTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onNavigate,
                onLongClick = onToggleSelection
            ),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = dateString, style = MaterialTheme.typography.titleMedium)
                if (isSelected) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "${workout.totalDistanceMeters}m", style = MaterialTheme.typography.bodyLarge)
                Text(text = formatTime(workout.totalSeconds), style = MaterialTheme.typography.bodyLarge)
                Text(text = stringResource(R.string.label_avg_power_format, workout.avgPower), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
