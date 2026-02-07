package com.syncrow.ui.training

import android.widget.NumberPicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.syncrow.R
import com.syncrow.data.db.DurationType
import com.syncrow.data.db.SegmentType
import com.syncrow.data.db.TrainingBlock
import com.syncrow.data.db.TrainingPlan
import com.syncrow.data.db.TrainingSegment
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingEditorScreen(
  viewModel: TrainingViewModel,
  planId: Long,
  onBack: () -> Unit,
  onStartWorkout: (Long) -> Unit
) {
  val plan by viewModel.editorPlan.collectAsState()
  val blocks by viewModel.editorBlocks.collectAsState()

  // If plan is null, we might be loading or something went wrong
  if (plan == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            if (planId == 0L) stringResource(R.string.title_create_plan)
            else stringResource(R.string.title_edit_plan)
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
          }
        },
        actions = {
          // Do Workout Button (Only if saved)
          if (planId != 0L) {
            IconButton(onClick = { onStartWorkout(planId) }) {
              Icon(Icons.Default.PlayArrow, "Start Workout")
            }
          }
          IconButton(
            onClick = {
              viewModel.savePlan()
              onBack()
            }
          ) {
            Icon(Icons.Default.Save, stringResource(R.string.btn_save_plan))
          }
        }
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Plan Details
      item { PlanDetailsEditor(plan = plan!!, onUpdate = { viewModel.updateEditorPlan(it) }) }

      // Blocks
      itemsIndexed(blocks) { index, editorBlock ->
        BlockEditor(
          index = index,
          block = editorBlock.block,
          segments = editorBlock.segments,
          onUpdateBlock = { viewModel.updateBlock(index, it) },
          onRemoveBlock = { viewModel.removeBlock(index) },
          onAddSegment = {
            viewModel.addSegmentToBlock(
              index,
              TrainingSegment(
                blockId = 0,
                orderIndex = 0,
                segmentType = SegmentType.ACTIVE.name,
                durationType = DurationType.TIME.name,
                durationValue = 60
              )
            )
          },
          onUpdateSegment = { sIndex, seg -> viewModel.updateSegment(index, sIndex, seg) },
          onRemoveSegment = { sIndex -> viewModel.removeSegment(index, sIndex) }
        )
      }

      item {
        Button(onClick = { viewModel.addBlock() }, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Default.Add, null)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.btn_add_block))
        }
      }
    }
  }
}

@Composable
fun PlanDetailsEditor(plan: TrainingPlan, onUpdate: (TrainingPlan) -> Unit) {
  // Map DB Value -> Localized String Res ID
  val difficultyOptions =
    listOf(
      "Beginner" to R.string.difficulty_beginner,
      "Intermediate" to R.string.difficulty_intermediate,
      "Advanced" to R.string.difficulty_advanced
    )
  val intensityOptions =
    listOf(
      "Low" to R.string.intensity_low,
      "Medium" to R.string.intensity_medium,
      "Hard" to R.string.intensity_hard
    )

  Card {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = plan.name,
        onValueChange = { onUpdate(plan.copy(name = it)) },
        label = { Text(stringResource(R.string.label_plan_name)) },
        modifier = Modifier.fillMaxWidth()
      )
      OutlinedTextField(
        value = plan.description,
        onValueChange = { onUpdate(plan.copy(description = it)) },
        label = { Text(stringResource(R.string.label_description)) },
        modifier = Modifier.fillMaxWidth()
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Find current display string
        val currentDiffRes = difficultyOptions.find { it.first == plan.difficulty }?.second
        val currentDiffDisplay =
          if (currentDiffRes != null) stringResource(currentDiffRes) else plan.difficulty

        DropdownField(
          label = stringResource(R.string.label_difficulty),
          options = difficultyOptions,
          selectedDisplay = currentDiffDisplay,
          onSelected = { onUpdate(plan.copy(difficulty = it)) },
          modifier = Modifier.weight(1f)
        )

        val currentIntRes = intensityOptions.find { it.first == plan.intensity }?.second
        val currentIntDisplay =
          if (currentIntRes != null) stringResource(currentIntRes) else plan.intensity

        DropdownField(
          label = stringResource(R.string.label_intensity),
          options = intensityOptions,
          selectedDisplay = currentIntDisplay,
          onSelected = { onUpdate(plan.copy(intensity = it)) },
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
fun DropdownField(
  label: String,
  options: List<Pair<String, Int>>, // DB Value -> Res ID
  selectedDisplay: String,
  onSelected: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    OutlinedTextField(
      value = selectedDisplay,
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Select") },
      modifier = Modifier.fillMaxWidth()
    )
    // Transparent clickable box to trigger dropdown
    Box(modifier = Modifier.matchParentSize().clickable { expanded = true })

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { (dbValue, resId) ->
        DropdownMenuItem(
          text = { Text(stringResource(resId)) },
          onClick = {
            onSelected(dbValue)
            expanded = false
          }
        )
      }
    }
  }
}

@Composable
fun BlockEditor(
  index: Int,
  block: TrainingBlock,
  segments: List<TrainingSegment>,
  onUpdateBlock: (TrainingBlock) -> Unit,
  onRemoveBlock: () -> Unit,
  onAddSegment: () -> Unit,
  onUpdateSegment: (Int, TrainingSegment) -> Unit,
  onRemoveSegment: (Int) -> Unit
) {
  Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(R.string.block_title, index + 1),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemoveBlock) {
          Icon(Icons.Default.Delete, stringResource(R.string.cd_remove_block))
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = block.name,
          onValueChange = { onUpdateBlock(block.copy(name = it)) },
          label = { Text(stringResource(R.string.label_block_name)) },
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = block.repeatCount.toString(),
          onValueChange = { onUpdateBlock(block.copy(repeatCount = it.toIntOrNull() ?: 1)) },
          label = { Text(stringResource(R.string.label_repeats)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.width(80.dp)
        )
      }

      HorizontalDivider()
      Text(stringResource(R.string.label_segments), style = MaterialTheme.typography.labelLarge)

      segments.forEachIndexed { sIndex, segment ->
        SegmentEditor(
          segment = segment,
          onUpdate = { onUpdateSegment(sIndex, it) },
          onRemove = { onRemoveSegment(sIndex) }
        )
        HorizontalDivider()
      }

      Button(onClick = onAddSegment, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.btn_add_segment))
      }
    }
  }
}

@Composable
fun SegmentEditor(
  segment: TrainingSegment,
  onUpdate: (TrainingSegment) -> Unit,
  onRemove: () -> Unit
) {
  Column(modifier = Modifier.padding(vertical = 8.dp)) {
    // Header Row: Type and Remove
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth()
    ) {
      SegmentTypeSelector(
        selected = SegmentType.valueOf(segment.segmentType),
        onSelected = { onUpdate(segment.copy(segmentType = it.name)) }
      )
      IconButton(onClick = onRemove) {
        Icon(Icons.Default.Delete, stringResource(R.string.cd_remove_segment), tint = Color.Gray)
      }
    }

    Spacer(Modifier.height(8.dp))

    // Duration Row
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      DurationTypeSelector(
        selected = DurationType.valueOf(segment.durationType),
        onSelected = { onUpdate(segment.copy(durationType = it.name)) },
        modifier = Modifier.width(120.dp)
      )

      if (segment.durationType == DurationType.TIME.name) {
        TimeInputButton(
          seconds = segment.durationValue,
          onValueChange = { onUpdate(segment.copy(durationValue = it)) },
          modifier = Modifier.weight(1f)
        )
      } else {
        OutlinedTextField(
          value = if (segment.durationValue == 0) "" else segment.durationValue.toString(),
          onValueChange = { onUpdate(segment.copy(durationValue = it.toIntOrNull() ?: 0)) },
          label = { Text(stringResource(R.string.label_meters)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f)
        )
      }
    }

    Spacer(Modifier.height(8.dp))

    // Targets Section
    Text(stringResource(R.string.label_targets), style = MaterialTheme.typography.labelMedium)

    // Existing Targets
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      if (segment.targetSpm != null) {
        TargetRow(stringResource(R.string.target_spm), segment.targetSpm.toString()) {
          onUpdate(segment.copy(targetSpm = it.toIntOrNull()))
        }
      }
      if (segment.targetWatts != null) {
        TargetRow(stringResource(R.string.target_watts), segment.targetWatts.toString()) {
          onUpdate(segment.copy(targetWatts = it.toIntOrNull()))
        }
      }
      if (segment.targetHr != null) {
        TargetRow(stringResource(R.string.target_hr), segment.targetHr.toString()) {
          onUpdate(segment.copy(targetHr = it.toIntOrNull()))
        }
      }
      if (segment.targetPace != null) {
        TargetRowPace(segment.targetPace) { onUpdate(segment.copy(targetPace = it)) }
      }
    }

    // Add Target Button
    var showTargetMenu by remember { mutableStateOf(false) }
    Box {
      TextButton(onClick = { showTargetMenu = true }) {
        Icon(Icons.Default.Add, null)
        Text(stringResource(R.string.btn_add_target))
      }
      DropdownMenu(expanded = showTargetMenu, onDismissRequest = { showTargetMenu = false }) {
        if (segment.targetSpm == null) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.target_spm)) },
            onClick = {
              onUpdate(segment.copy(targetSpm = 20))
              showTargetMenu = false
            }
          )
        }
        if (segment.targetWatts == null) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.target_watts)) },
            onClick = {
              onUpdate(segment.copy(targetWatts = 150))
              showTargetMenu = false
            }
          )
        }
        if (segment.targetPace == null) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.target_pace)) },
            onClick = {
              onUpdate(segment.copy(targetPace = 120)) // 2:00
              showTargetMenu = false
            }
          )
        }
        if (segment.targetHr == null) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.target_hr)) },
            onClick = {
              onUpdate(segment.copy(targetHr = 140))
              showTargetMenu = false
            }
          )
        }
      }
    }
  }
}

@Composable
fun SegmentTypeSelector(selected: SegmentType, onSelected: (SegmentType) -> Unit) {
  var expanded by remember { mutableStateOf(false) }

  // Helper to get localized name
  val typeName =
    when (selected) {
      SegmentType.ACTIVE -> stringResource(R.string.segment_type_active)
      SegmentType.RECOVERY -> stringResource(R.string.segment_type_recovery)
      SegmentType.WARMUP -> stringResource(R.string.segment_type_warmup)
      SegmentType.COOLDOWN -> stringResource(R.string.segment_type_cooldown)
    }

  Box {
    OutlinedButton(onClick = { expanded = true }) { Text(typeName) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      SegmentType.values().forEach { type ->
        val itemTypeName =
          when (type) {
            SegmentType.ACTIVE -> stringResource(R.string.segment_type_active)
            SegmentType.RECOVERY -> stringResource(R.string.segment_type_recovery)
            SegmentType.WARMUP -> stringResource(R.string.segment_type_warmup)
            SegmentType.COOLDOWN -> stringResource(R.string.segment_type_cooldown)
          }
        DropdownMenuItem(
          text = { Text(itemTypeName) },
          onClick = {
            onSelected(type)
            expanded = false
          }
        )
      }
    }
  }
}

@Composable
fun DurationTypeSelector(
  selected: DurationType,
  onSelected: (DurationType) -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }

  val typeName =
    when (selected) {
      DurationType.TIME -> stringResource(R.string.duration_type_time)
      DurationType.DISTANCE -> stringResource(R.string.duration_type_distance)
    }

  Box(modifier = modifier) {
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
      Text(typeName)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DurationType.values().forEach { type ->
        val itemTypeName =
          when (type) {
            DurationType.TIME -> stringResource(R.string.duration_type_time)
            DurationType.DISTANCE -> stringResource(R.string.duration_type_distance)
          }
        DropdownMenuItem(
          text = { Text(itemTypeName) },
          onClick = {
            onSelected(type)
            expanded = false
          }
        )
      }
    }
  }
}

@Composable
fun TargetRow(label: String, value: String, onValueChange: (String) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
      modifier = Modifier.weight(1f),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      trailingIcon = {
        IconButton(onClick = { onValueChange("") }) { // Treat empty as delete
          Icon(Icons.Default.Close, stringResource(R.string.cd_remove_target))
        }
      }
    )
  }
}

@Composable
fun TargetRowPace(seconds: Int, onValueChange: (Int?) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    TimeInputButton(
      seconds = seconds,
      onValueChange = { onValueChange(it) },
      label = stringResource(R.string.target_pace),
      modifier = Modifier.weight(1f)
    )
    IconButton(onClick = { onValueChange(null) }) {
      Icon(Icons.Default.Close, stringResource(R.string.cd_remove_target))
    }
  }
}

@Composable
fun TimeInputButton(
  seconds: Int,
  onValueChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  label: String = stringResource(R.string.label_duration)
) {
  var showDialog by remember { mutableStateOf(false) }

  val mins = seconds / 60
  val secs = seconds % 60
  val timeText = String.format(Locale.getDefault(), "%d:%02d", mins, secs)

  OutlinedButton(onClick = { showDialog = true }, modifier = modifier) {
    Column(horizontalAlignment = Alignment.Start) {
      Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(timeText, style = MaterialTheme.typography.bodyLarge)
    }
  }

  if (showDialog) {
    DurationPickerDialog(
      initialSeconds = seconds,
      onConfirm = {
        onValueChange(it)
        showDialog = false
      },
      onDismiss = { showDialog = false }
    )
  }
}

@Composable
fun DurationPickerDialog(initialSeconds: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
  val initialMins = initialSeconds / 60
  val initialSecs = initialSeconds % 60

  var selectedMins by remember { mutableIntStateOf(initialMins) }
  var selectedSecs by remember { mutableIntStateOf(initialSecs) }
  val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = MaterialTheme.shapes.extraLarge,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          stringResource(R.string.label_set_duration),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Minutes Picker
          AndroidView(
            modifier = Modifier.width(64.dp),
            factory = { context ->
              val themedContext =
                android.view.ContextThemeWrapper(
                  context,
                  if (isDark) android.R.style.Theme_DeviceDefault
                  else android.R.style.Theme_DeviceDefault_Light
                )
              NumberPicker(themedContext).apply {
                minValue = 0
                maxValue = 59
                value = initialMins
                setOnValueChangedListener { _, _, newVal -> selectedMins = newVal }
              }
            }
          )
          Text(
            stringResource(R.string.label_min),
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
          )

          // Seconds Picker
          AndroidView(
            modifier = Modifier.width(64.dp),
            factory = { context ->
              val themedContext =
                android.view.ContextThemeWrapper(
                  context,
                  if (isDark) android.R.style.Theme_DeviceDefault
                  else android.R.style.Theme_DeviceDefault_Light
                )
              NumberPicker(themedContext).apply {
                minValue = 0
                maxValue = 59
                value = initialSecs
                setFormatter { i -> String.format(Locale.getDefault(), "%02d", i) }
                setOnValueChangedListener { _, _, newVal -> selectedSecs = newVal }
              }
            }
          )
          Text(
            stringResource(R.string.label_sec),
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel_dialog)) }
          TextButton(onClick = { onConfirm(selectedMins * 60 + selectedSecs) }) {
            Text(stringResource(R.string.btn_ok))
          }
        }
      }
    }
  }
}
