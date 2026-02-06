package com.syncrow.ui.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.syncrow.R
import com.syncrow.data.db.TrainingPlan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingListScreen(
  viewModel: TrainingViewModel,
  onNavigateToEditor: (Long) -> Unit,
  onBack: () -> Unit
) {
  val plans by viewModel.filteredPlans.collectAsState(initial = emptyList())
  val sortOrder by viewModel.sortOrder.collectAsState()
  val filterDifficulty by viewModel.filterDifficulty.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.title_training_plans)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back)
            )
          }
        },
        actions = {
          if (sortOrder != "Difficulty (Beginner-Advanced)" || filterDifficulty != "All") {
            IconButton(onClick = { viewModel.resetFilters() }) {
              Icon(Icons.Default.Clear, stringResource(R.string.cd_reset_filters))
            }
          }
          SortMenu(currentSort = sortOrder, onSortSelected = { viewModel.setSortOrder(it) })
          FilterMenu(
            currentFilter = filterDifficulty,
            onFilterSelected = { viewModel.setFilterDifficulty(it) }
          )
        }
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          viewModel.createNewPlan()
          onNavigateToEditor(0)
        }
      ) {
        Icon(Icons.Default.Add, stringResource(R.string.cd_create_plan))
      }
    }
  ) { padding ->
    if (plans.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.label_no_plans))
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(plans) { plan ->
          TrainingPlanItem(
            plan = plan,
            onClick = {
              viewModel.loadPlanForEditing(plan.id)
              onNavigateToEditor(plan.id)
            },
            onFavorite = { viewModel.toggleFavorite(plan) },
            onDelete = { viewModel.deletePlan(plan) },
            onCopy = { viewModel.copyPlan(plan.id) }
          )
        }
      }
    }
  }
}

@Composable
fun SortMenu(currentSort: String, onSortSelected: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }

  // Map of ViewModel Key -> Resource ID
  val sortOptions =
    listOf(
      "Difficulty (Beginner-Advanced)" to R.string.sort_difficulty_asc,
      "Difficulty (Advanced-Beginner)" to R.string.sort_difficulty_desc,
      "Name" to R.string.sort_name,
      "Intensity" to R.string.sort_intensity,
      "Created Date" to R.string.sort_date
    )

  IconButton(onClick = { expanded = true }) {
    Icon(Icons.Default.Sort, stringResource(R.string.cd_sort))
  }
  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    sortOptions.forEach { (key, resId) ->
      DropdownMenuItem(
        text = { Text(stringResource(resId)) },
        onClick = {
          onSortSelected(key)
          expanded = false
        },
        trailingIcon = {
          if (key == currentSort) {
            // Checkmark could go here
          }
        }
      )
    }
  }
}

@Composable
fun FilterMenu(currentFilter: String, onFilterSelected: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }

  // Map of ViewModel Key -> Resource ID
  val filterOptions =
    listOf(
      "All" to R.string.filter_all,
      "Favorites" to R.string.filter_favorites,
      "Beginner" to R.string.difficulty_beginner,
      "Intermediate" to R.string.difficulty_intermediate,
      "Advanced" to R.string.difficulty_advanced
    )

  IconButton(onClick = { expanded = true }) {
    Icon(Icons.Default.FilterList, stringResource(R.string.cd_filter))
  }
  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    filterOptions.forEach { (key, resId) ->
      DropdownMenuItem(
        text = { Text(stringResource(resId)) },
        onClick = {
          onFilterSelected(key)
          expanded = false
        },
        trailingIcon = {
          if (key == currentFilter) {
            // Checkmark could go here
          }
        }
      )
    }
  }
}

@Composable
fun TrainingPlanItem(
  plan: TrainingPlan,
  onClick: () -> Unit,
  onFavorite: () -> Unit,
  onDelete: () -> Unit,
  onCopy: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(plan.name, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onFavorite) {
          Icon(
            if (plan.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = stringResource(R.string.cd_favorite),
            tint = if (plan.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
          )
        }
      }
      Text(plan.description, style = MaterialTheme.typography.bodyMedium)
      Spacer(modifier = Modifier.height(8.dp))
      Row {
        // Map DB values to Localized strings
        val diffString =
          when (plan.difficulty) {
            "Beginner" -> stringResource(R.string.difficulty_beginner)
            "Intermediate" -> stringResource(R.string.difficulty_intermediate)
            "Advanced" -> stringResource(R.string.difficulty_advanced)
            else -> plan.difficulty
          }
        val intensityString =
          when (plan.intensity) {
            "Low" -> stringResource(R.string.intensity_low)
            "Medium" -> stringResource(R.string.intensity_medium)
            "Hard" -> stringResource(R.string.intensity_hard)
            else -> plan.intensity
          }

        Badge(text = diffString)
        Spacer(modifier = Modifier.width(8.dp))
        Badge(text = intensityString)
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onCopy) {
          Icon(Icons.Default.ContentCopy, stringResource(R.string.cd_copy))
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Default.Delete, stringResource(R.string.cd_delete))
        }
      }
    }
  }
}

@Composable
fun Badge(text: String) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.small
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSecondaryContainer
    )
  }
}
