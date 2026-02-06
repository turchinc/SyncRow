package com.syncrow.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncrow.R
import com.syncrow.ui.workout.WorkoutViewModel

@Composable
fun HomeScreen(
    viewModel: WorkoutViewModel,
    onStartWorkout: () -> Unit,
    onNavigateToDiscovery: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onQuit: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF001220) // Deep Blue
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Main Content Centered
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                // User info as a subtitle adds context to the session
                Text(
                    text = stringResource(R.string.label_current_user, currentUser?.name ?: stringResource(R.string.loading)),
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 48.dp)
                )

                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)) // Green
                ) {
                    Text(stringResource(R.string.btn_just_row), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(stringResource(R.string.btn_history), fontSize = 18.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = onNavigateToDiscovery,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(stringResource(R.string.btn_hardware), fontSize = 14.sp)
                    }
                    
                    OutlinedButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(stringResource(R.string.btn_profile), fontSize = 14.sp)
                    }
                }
            }

            // Footer for secondary actions
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onNavigateToAbout) {
                    Text(
                        stringResource(R.string.btn_about), 
                        color = Color.Gray, 
                        fontSize = 14.sp
                    )
                }
                
                Text(
                    text = "•",
                    color = Color.DarkGray,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                TextButton(onClick = onQuit) {
                    Text(
                        stringResource(R.string.btn_quit), 
                        color = Color.Gray, 
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
