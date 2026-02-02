package com.syncrow

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.syncrow.ui.SyncRowNavGraph
import com.syncrow.ui.workout.WorkoutViewModel
import com.syncrow.ui.workout.ToastEvent

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as SyncRowApplication
        val rxBleClient = app.rxBleClient
        val db = app.database

        setContent {
            val context = LocalContext.current
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions -> }

            LaunchedEffect(Unit) {
                launcher.launch(permissionsToRequest)
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: WorkoutViewModel = viewModel(
                        factory = WorkoutViewModel.Factory(
                            app,
                            rxBleClient,
                            db.userDao(),
                            db.workoutDao(),
                            db.metricPointDao()
                        )
                    )

                    // Collect and show toast messages from ViewModel
                    LaunchedEffect(viewModel.toastEvent) {
                        viewModel.toastEvent.collect { event ->
                            when (event) {
                                is ToastEvent.Resource -> {
                                    val message = if (event.args.isEmpty()) {
                                        context.getString(event.resId)
                                    } else {
                                        context.getString(event.resId, *event.args.toTypedArray())
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    SyncRowNavGraph(
                        viewModel = viewModel,
                        onQuit = { finish() }
                    )
                }
            }
        }
    }
}
