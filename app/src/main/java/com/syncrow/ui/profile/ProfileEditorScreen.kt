package com.syncrow.ui.profile

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.syncrow.BuildConfig
import com.syncrow.R
import com.syncrow.ui.workout.WorkoutViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(viewModel: WorkoutViewModel, onBack: () -> Unit) {
  val user by viewModel.currentUser.collectAsState()
  val isCloudPermanent by viewModel.isCloudPermanent.collectAsState()
  val context = LocalContext.current

  if (user == null) {
    onBack()
    return
  }

  val currentUser = user!!

  var name by remember { mutableStateOf(currentUser.name) }
  var age by remember { mutableStateOf(currentUser.age?.toString() ?: "") }
  var weight by remember { mutableStateOf(currentUser.weightKg?.toString() ?: "") }
  var height by remember { mutableStateOf(currentUser.heightCm?.toString() ?: "") }
  var gender by remember { mutableStateOf(currentUser.gender ?: "") }
  var languageCode by remember { mutableStateOf(currentUser.languageCode) }
  var themeMode by remember { mutableStateOf(currentUser.themeMode) }
  var autoUploadToStrava by remember { mutableStateOf(currentUser.autoUploadToStrava) }
  var cloudSyncEnabled by remember { mutableStateOf(currentUser.cloudSyncEnabled) }

  val genders = listOf("Male", "Female", "Other")
  val languages = listOf("en", "fr", "de", "es", "it")
  val themes = listOf("SYSTEM", "LIGHT", "DARK")

  var expandedLang by remember { mutableStateOf(false) }
  var expandedTheme by remember { mutableStateOf(false) }

  val googleSignInLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
      try {
        val account = task.getResult(ApiException::class.java)
        account.idToken?.let { viewModel.linkWithGoogle(it) }
      } catch (e: ApiException) {
        Log.e("SyncRow", "Google Sign-In failed", e)
      }
    }

  val onSignInGoogle = {
    // SECURE: Use the client ID from BuildConfig (which pulls from local.properties/env)
    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val gso =
      GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(clientId)
        .requestEmail()
        .build()
    val client = GoogleSignIn.getClient(context, gso)
    googleSignInLauncher.launch(client.signInIntent)
  }

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

    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
      context.startActivity(intent)
    } catch (e: Exception) {
      Log.e("SyncRow", "Could not open browser", e)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.cd_edit_profile)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          TextButton(
            onClick = {
              viewModel.updateCurrentUser(
                currentUser.copy(
                  name = name,
                  age = age.toIntOrNull(),
                  weightKg = weight.toDoubleOrNull(),
                  heightCm = height.toIntOrNull(),
                  gender = gender,
                  languageCode = languageCode,
                  themeMode = themeMode,
                  autoUploadToStrava = autoUploadToStrava,
                  cloudSyncEnabled = cloudSyncEnabled
                )
              )
              onBack()
            }
          ) {
            Text(stringResource(R.string.btn_save))
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .padding(16.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(
        stringResource(R.string.label_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth(),
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

      Text(stringResource(R.string.label_gender), style = MaterialTheme.typography.titleMedium)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

      HorizontalDivider()

      // Preferences
      Text(
        stringResource(R.string.label_theme),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
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

      Text(
        stringResource(R.string.label_language),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
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
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLang) },
          modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expandedLang, onDismissRequest = { expandedLang = false }) {
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

      HorizontalDivider()

      // Cloud Sync Section
      Text(
        stringResource(R.string.label_cloud_sync_settings),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().clickable { cloudSyncEnabled = !cloudSyncEnabled }
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              stringResource(R.string.label_cloud_sync),
              style = MaterialTheme.typography.bodyLarge
            )
            Text(
              stringResource(R.string.description_cloud_sync),
              style = MaterialTheme.typography.bodySmall,
              color = Color.Gray
            )
          }
          Switch(checked = cloudSyncEnabled, onCheckedChange = { cloudSyncEnabled = it })
        }

        if (cloudSyncEnabled) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            stringResource(R.string.label_sync_mode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
          )

          Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text(
                stringResource(R.string.description_sync_mode),
                style = MaterialTheme.typography.bodySmall
              )

              if (!isCloudPermanent) {
                Button(
                  onClick = onSignInGoogle,
                  modifier = Modifier.fillMaxWidth(),
                  colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                  Icon(Icons.Default.AccountCircle, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(stringResource(R.string.btn_sign_in_google))
                }

                Text(
                  stringResource(R.string.sync_mode_anon),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.align(Alignment.CenterHorizontally)
                )
              } else {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.Green,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    stringResource(R.string.sync_mode_google),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }
          }
        }
      }

      HorizontalDivider()

      // Strava Section
      Text(
        stringResource(R.string.label_strava_settings),
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFFFC4C02)
      )

      if (currentUser.stravaToken == null) {
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().clickable { autoUploadToStrava = !autoUploadToStrava }
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              stringResource(R.string.label_auto_upload),
              style = MaterialTheme.typography.bodyLarge
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

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}
