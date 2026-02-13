package com.syncrow.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncrow.R
import com.syncrow.ui.workout.WorkoutViewModel

@Composable
fun HomeScreen(
  viewModel: WorkoutViewModel,
  onStartWorkout: () -> Unit,
  onNavigateToTraining: () -> Unit,
  onNavigateToDiscovery: () -> Unit,
  onNavigateToProfile: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onNavigateToAbout: () -> Unit,
  onQuit: () -> Unit
) {
  val currentUser by viewModel.currentUser.collectAsState()
  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  val userName = currentUser?.name ?: stringResource(R.string.loading)

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    // Root Column ensures Header, Content, and Footer never overlap
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 1. Header Section - Always at the top
      Spacer(modifier = Modifier.height(if (isLandscape) 0.dp else 24.dp))
      Text(
        text = stringResource(R.string.app_name).uppercase(),
        fontSize = if (isLandscape) 28.sp else 48.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary
      )
      Text(
        text = stringResource(R.string.label_current_user, userName),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
      )

      // 2. Main Content Area - Responsive Buttons
      // weight(1f) expands to fill middle space, pushing footer down
      Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (isLandscape) {
          LandscapeButtonLayout(
            onStartWorkout,
            onNavigateToHistory,
            onNavigateToTraining,
            onNavigateToDiscovery,
            onNavigateToProfile
          )
        } else {
          PortraitButtonLayout(
            onStartWorkout,
            onNavigateToHistory,
            onNavigateToTraining,
            onNavigateToDiscovery,
            onNavigateToProfile
          )
        }
      }

      // 3. Footer Section - Safe at the bottom
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        TextButton(onClick = onNavigateToAbout) {
          Text(
            stringResource(R.string.btn_about),
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 14.sp
          )
        }
        Text(
          text = "•",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 8.dp)
        )
        TextButton(onClick = onQuit) {
          Text(
            stringResource(R.string.btn_quit),
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 14.sp
          )
        }
      }
    }
  }
}

@Composable
private fun PortraitButtonLayout(
  onStartWorkout: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onNavigateToTraining: () -> Unit,
  onNavigateToDiscovery: () -> Unit,
  onNavigateToProfile: () -> Unit
) {
  Column(
    modifier = Modifier.widthIn(max = 400.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Button(
      onClick = onStartWorkout,
      modifier = Modifier.fillMaxWidth().height(64.dp),
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)) // Green
    ) {
      Text(
        stringResource(R.string.btn_just_row),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      SecondaryButton(
        stringResource(R.string.btn_history),
        onNavigateToHistory,
        Modifier.weight(1f)
      )
      SecondaryButton(
        stringResource(R.string.btn_training),
        onNavigateToTraining,
        Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      SecondaryButton(
        stringResource(R.string.btn_hardware),
        onNavigateToDiscovery,
        Modifier.weight(1f)
      )
      SecondaryButton(
        stringResource(R.string.btn_profile),
        onNavigateToProfile,
        Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun LandscapeButtonLayout(
  onStartWorkout: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onNavigateToTraining: () -> Unit,
  onNavigateToDiscovery: () -> Unit,
  onNavigateToProfile: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(24.dp)
  ) {
    // JUST ROW on the left - reasonable height for landscape
    Button(
      onClick = onStartWorkout,
      modifier = Modifier.weight(1f).height(64.dp),
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)) // Green
    ) {
      Text(
        stringResource(R.string.btn_just_row),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
      )
    }

    // Secondary buttons on the right in a compact 2x2 grid
    Column(modifier = Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(
          stringResource(R.string.btn_history),
          onNavigateToHistory,
          Modifier.weight(1f),
          height = 44.dp
        )
        SecondaryButton(
          stringResource(R.string.btn_training),
          onNavigateToTraining,
          Modifier.weight(1f),
          height = 44.dp
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(
          stringResource(R.string.btn_hardware),
          onNavigateToDiscovery,
          Modifier.weight(1f),
          height = 44.dp
        )
        SecondaryButton(
          stringResource(R.string.btn_profile),
          onNavigateToProfile,
          Modifier.weight(1f),
          height = 44.dp
        )
      }
    }
  }
}

@Composable
private fun SecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  height: androidx.compose.ui.unit.Dp = 56.dp
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.height(height),
    shape = RoundedCornerShape(12.dp),
    contentPadding = PaddingValues(horizontal = 8.dp),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
  ) {
    Text(
      text = text,
      textAlign = TextAlign.Center,
      maxLines = 1,
      fontSize = if (height < 50.dp) 13.sp else 14.sp
    )
  }
}
