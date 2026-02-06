package com.syncrow

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.syncrow.ui.SyncRowNavGraph
import com.syncrow.ui.workout.ToastEvent
import com.syncrow.ui.workout.WorkoutViewModel

class MainActivity : AppCompatActivity() {

  private lateinit var viewModel: WorkoutViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val app = application as SyncRowApplication
    val rxBleClient = app.rxBleClient
    val db = app.database
    val stravaRepository = app.stravaRepository

    setContent {
      val context = LocalContext.current
      val permissionsToRequest =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        } else {
          arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        }

      val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          permissions ->
        }

      LaunchedEffect(Unit) { launcher.launch(permissionsToRequest) }

      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          viewModel =
            viewModel(
              factory =
                WorkoutViewModel.Factory(
                  app,
                  rxBleClient,
                  db.userDao(),
                  db.workoutDao(),
                  db.metricPointDao(),
                  db.splitDao(), // Added missing argument
                  stravaRepository
                )
            )

          // Collect and show toast messages from ViewModel
          LaunchedEffect(viewModel.toastEvent) {
            viewModel.toastEvent.collect { event ->
              val message =
                when (event) {
                  is ToastEvent.Resource -> {
                    if (event.args.isEmpty()) {
                      context.getString(event.resId)
                    } else {
                      context.getString(event.resId, *event.args.toTypedArray())
                    }
                  }
                  is ToastEvent.String -> event.message
                }
              Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
          }

          SyncRowNavGraph(viewModel = viewModel, onQuit = { finish() })
        }
      }
    }

    handleIntent(intent)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    intent?.data?.let { uri ->
      Log.d("SyncRow", "Received Intent URI: $uri")
      // Handle both custom scheme and legacy http://localhost
      if (
        (uri.scheme == "syncrow" && uri.host == "strava-auth") ||
          (uri.scheme == "http" && uri.host == "localhost")
      ) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
          Log.d("SyncRow", "Found Auth Code: $code")
          if (::viewModel.isInitialized) {
            viewModel.completeStravaAuth(code)
          }
        }
      }
    }
  }
}
