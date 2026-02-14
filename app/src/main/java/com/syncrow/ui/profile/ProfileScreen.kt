package com.syncrow.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.syncrow.R
import com.syncrow.data.db.User
import com.syncrow.ui.workout.WorkoutViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: WorkoutViewModel, onBack: () -> Unit, onNavigateToEditor: () -> Unit) {
  val users by viewModel.allUsers.collectAsState(initial = emptyList())
  val currentUser by viewModel.currentUser.collectAsState()

  var showAddDialog by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.title_user_profiles)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          IconButton(onClick = { showAddDialog = true }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_profile))
          }
        }
      )
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      currentUser?.let { user ->
        Text(
          stringResource(R.string.label_active_profile),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        ActiveProfileCard(user) { onNavigateToEditor() }
        Spacer(modifier = Modifier.height(24.dp))
      }

      Text(
        stringResource(R.string.label_switch_profile),
        style = MaterialTheme.typography.labelLarge,
        color = Color.Gray
      )
      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn(modifier = Modifier.weight(1f)) {
        items(users.filter { it.id != currentUser?.id }) { user ->
          ProfileItem(user) { viewModel.switchUser(user) }
        }
      }
    }

    if (showAddDialog) {
      AddProfileDialog(
        onDismiss = { showAddDialog = false },
        onConfirm = { name ->
          viewModel.addUser(name)
          showAddDialog = false
        }
      )
    }
  }
}

@Composable
fun ActiveProfileCard(user: User, onEdit: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = user.name, style = MaterialTheme.typography.headlineSmall)
          val details =
            listOfNotNull(
                when (user.gender) {
                  "Male" -> stringResource(R.string.gender_male)
                  "Female" -> stringResource(R.string.gender_female)
                  "Other" -> stringResource(R.string.gender_other)
                  else -> user.gender
                },
                user.age?.let { stringResource(R.string.label_years_format, it) },
                user.weightKg?.let { "${it}kg" },
                user.heightCm?.let { "${it}cm" }
              )
              .joinToString(" • ")
          if (details.isNotEmpty()) {
            Text(text = details, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
          }
        }
        IconButton(onClick = onEdit) {
          Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_profile))
        }
      }

      if (user.stravaToken != null || user.cloudSyncEnabled) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (user.stravaToken != null) {
            Icon(
              Icons.Default.Link,
              contentDescription = null,
              tint = Color(0xFFFC4C02),
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              stringResource(R.string.label_strava_connected),
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFFFC4C02)
            )
            if (user.cloudSyncEnabled) {
              Spacer(modifier = Modifier.width(16.dp))
            }
          }
          if (user.cloudSyncEnabled) {
            Icon(
              Icons.Default.CloudDone,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              stringResource(R.string.label_cloud_sync),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }
    }
  }
}

@Composable
fun ProfileItem(user: User, onClick: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = user.name,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f)
      )
      Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
  }
}

@Composable
fun AddProfileDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  var name by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.title_new_profile)) },
    text = {
      TextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.label_name)) },
        singleLine = true
      )
    },
    confirmButton = {
      Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
        Text(stringResource(R.string.btn_create))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
    }
  )
}
