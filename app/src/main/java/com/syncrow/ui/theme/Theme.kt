package com.syncrow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Gemini High-Contrast Dark Theme
private val DarkColorScheme =
  darkColorScheme(
    primary = BrightCyan, // High-visibility Cyan for "JUST ROW"
    onPrimary = DeepNavy, // Text on primary button (Dark on Light)
    primaryContainer = MutedNavy,
    onPrimaryContainer = BrightCyan,
    secondary = MutedNavy, // Secondary buttons fill
    onSecondary = PureWhite, // Text on secondary buttons (White on Navy)
    secondaryContainer = MutedNavy,
    onSecondaryContainer = PureWhite,
    tertiary = SlateGrey, // Utility text / accents
    onTertiary = DeepNavy,
    background = DeepNavy, // Deep Navy background
    onBackground = PureWhite, // Primary text
    surface = DeepNavy, // Surface color matching background for clean look
    onSurface = PureWhite, // Text on surface
    surfaceVariant = MutedNavy, // Alternate surface color (cards)
    onSurfaceVariant = SlateGrey, // Secondary text color
    error = Color(0xFFCF6679),
    onError = DeepNavy
  )

// "Ocean Tech" Light Theme (Updated to match new palette slightly)
private val LightColorScheme =
  lightColorScheme(
    primary = Teal,
    onPrimary = PureWhite,
    primaryContainer = BrightCyan.copy(alpha = 0.2f),
    onPrimaryContainer = DeepNavy,
    secondary = MutedNavy,
    onSecondary = PureWhite,
    secondaryContainer = MutedNavy.copy(alpha = 0.1f),
    onSecondaryContainer = DeepNavy,
    tertiary = SlateGrey,
    onTertiary = PureWhite,
    background = LightGray,
    onBackground = TextDark,
    surface = PureWhite,
    onSurface = DeepNavy,
    surfaceVariant = LightGray,
    onSurfaceVariant = DeepNavy,
    error = Color(0xFFBA1A1A),
    onError = PureWhite
  )

@Composable
fun SyncRowTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default to enforce brand colors
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = MaterialTheme.typography, content = content)
}
