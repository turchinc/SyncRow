package com.syncrow.ui.profile

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.syncrow.BuildConfig
import com.syncrow.R
import com.syncrow.data.db.User
import com.syncrow.ui.workout.WorkoutViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: WorkoutViewModel, onBack: () -> Unit) {
  val users by viewModel.allUsers.collectAsState(initial = emptyList())
  val currentUser by viewModel.currentUser.collectAsState()

  var showAddDialog by remember { mutableStateOf(false) }
  var showEditDialog by remember { mutableStateOf(false) }

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
        ActiveProfileCard(user) { showEditDialog = true }
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

    if (showEditDialog) {
      currentUser?.let { user ->
        EditProfileDialog(
          user = user,
          viewModel = viewModel,
          onDismiss = { showEditDialog = false },
          onConfirm = { updatedUser ->
            viewModel.updateCurrentUser(updatedUser)
            showEditDialog = false
          }
        )
      }
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

      if (user.stravaToken != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
  user: User,
  viewModel: WorkoutViewModel,
  onDismiss: () -> Unit,
  onConfirm: (User) -> Unit
) {
  val context = LocalContext.current
  var name by remember { mutableStateOf(user.name) }
  var age by remember { mutableStateOf(user.age?.toString() ?: "") }
  var weight by remember { mutableStateOf(user.weightKg?.toString() ?: "") }
  var height by remember { mutableStateOf(user.heightCm?.toString() ?: "") }
  var gender by remember { mutableStateOf(user.gender ?: "") }
  var languageCode by remember { mutableStateOf(user.languageCode) }
  var themeMode by remember { mutableStateOf(user.themeMode) }
  var autoUploadToStrava by remember { mutableStateOf(user.autoUploadToStrava) }

  val genders = listOf("Male", "Female", "Other")
  val languages = listOf("en", "fr", "de", "es", "it")
  val themes = listOf("SYSTEM", "LIGHT", "DARK")

  var expandedLang by remember { mutableStateOf(false) }
  var expandedTheme by remember { mutableStateOf(false) }

  val onConnectStrava: () -> Unit = {
    val clientId = BuildConfig.STRAVA_CLIENT_ID.replace("\"", "").trim()
    val redirectUri = "syncrow://strava-auth"
    val scope = "activity:write,read"

    val uri =
      Uri.parse("https://www.strava.com/oauth/authorize")
        .buildUpon()
        .appendQueryParameter("client_id", clientId)
        .appendQueryParameter("redirect_uri", redirectUri)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("scope", scope)
        .appendQueryParameter("approval_prompt", "force")
        .build()

    Log.d("SyncRow", "Opening Strava Auth: $uri")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
      context.startActivity(intent)
    } catch (e: Exception) {
      Log.e("SyncRow", "Could not open browser", e)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.cd_edit_profile)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.label_name)) },
          singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = age,
            onValueChange = { age = it },
            label = { Text(stringResource(R.string.label_age)) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
          )
          OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text(stringResource(R.string.label_weight_kg)) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
        }

        OutlinedTextField(
          value = height,
          onValueChange = { height = it },
          label = { Text(stringResource(R.string.label_height_cm)) },
          modifier = Modifier.fillMaxWidth(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true
        )

        // Theme Selection
        ExposedDropdownMenuBox(
          expanded = expandedTheme,
          onExpandedChange = { expandedTheme = !expandedTheme }
        ) {
          OutlinedTextField(
            value =
              when (themeMode) {
                "LIGHT" -> stringResource(R.string.theme_light)
                "DARK" -> stringResource(R.string.theme_dark)
                else -> stringResource(R.string.theme_system)
              },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.label_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTheme) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = expandedTheme,
            onDismissRequest = { expandedTheme = false }
          ) {
            themes.forEach { mode ->
              DropdownMenuItem(
                text = {
                  Text(
                    when (mode) {
                      "LIGHT" -> stringResource(R.string.theme_light)
                      "DARK" -> stringResource(R.string.theme_dark)
                      else -> stringResource(R.string.theme_system)
                    }
                  )
                },
                onClick = {
                  themeMode = mode
                  expandedTheme = false
                }
              )
            }
          }
        }

        // Language Selection
        ExposedDropdownMenuBox(
          expanded = expandedLang,
          onExpandedChange = { expandedLang = !expandedLang }
        ) {
          OutlinedTextField(
            value =
              when (languageCode) {
                "fr" -> stringResource(R.string.lang_fr)
                "de" -> stringResource(R.string.lang_de)
                "es" -> stringResource(R.string.lang_es)
                "it" -> stringResource(R.string.lang_it)
                else -> stringResource(R.string.lang_en)
              },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.label_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLang) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
          )
          ExposedDropdownMenu(
            expanded = expandedLang,
            onDismissRequest = { expandedLang = false }
          ) {
            languages.forEach { code ->
              DropdownMenuItem(
                text = {
                  Text(
                    when (code) {
                      "fr" -> stringResource(R.string.lang_fr)
                      "de" -> stringResource(R.string.lang_de)
                      "es" -> stringResource(R.string.lang_es)
                      "it" -> stringResource(R.string.lang_it)
                      else -> stringResource(R.string.lang_en)
                    }
                  )
                },
                onClick = {
                  languageCode = code
                  expandedLang = false
                }
              )
            }
          }
        }

        Text(stringResource(R.string.label_gender), style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          genders.forEach { g ->
            val genderLabel =
              when (g) {
                "Male" -> stringResource(R.string.gender_male)
                "Female" -> stringResource(R.string.gender_female)
                "Other" -> stringResource(R.string.gender_other)
                else -> g
              }
            FilterChip(
              selected = gender == g,
              onClick = { gender = g },
              label = { Text(genderLabel) }
            )
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(
          stringResource(R.string.section_connected_apps),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary
        )

        // Strava Section
        Text(
          stringResource(R.string.label_strava_settings),
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.padding(top = 8.dp)
        )

        if (user.stravaToken == null) {
          Button(
            onClick = onConnectStrava,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFC4C02))
          ) {
            Icon(Icons.Default.Link, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_connect_strava))
          }
        } else {
          // Auto-upload switch
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                autoUploadToStrava = !autoUploadToStrava
              }
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                stringResource(R.string.label_auto_upload),
                style = MaterialTheme.typography.bodyMedium
              )
              Text(
                stringResource(R.string.description_auto_upload),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
              )
            }
            Switch(checked = autoUploadToStrava, onCheckedChange = { autoUploadToStrava = it })
          }

          OutlinedButton(
            onClick = { viewModel.disconnectStrava() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFC4C02))
          ) {
            Icon(Icons.Default.LinkOff, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_disconnect_strava))
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm(
            user.copy(
              name = name,
              age = age.toIntOrNull(),
              weightKg = weight.toDoubleOrNull(),
              heightCm = height.toIntOrNull(),
              gender = gender,
              languageCode = languageCode,
              themeMode = themeMode,
              autoUploadToStrava = autoUploadToStrava
            )
          )
        }
      ) {
        Text(stringResource(R.string.btn_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
    }
  )
}
