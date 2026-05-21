package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = KiranaPrimaryDark,
    primaryContainer = KiranaPrimaryContainerDark,
    onPrimary = KiranaOnPrimaryDark,
    onPrimaryContainer = KiranaOnPrimaryContainerDark,
    background = KiranaBackgroundDark,
    onBackground = KiranaOnBackgroundDark,
    surface = KiranaSurfaceDark,
    onSurface = KiranaOnSurfaceDark,
    surfaceVariant = KiranaSurfaceVariantDark,
    onSurfaceVariant = KiranaOnSurfaceVariantDark,
    outline = KiranaOutline,
    secondary = KiranaPrimaryDark,
    secondaryContainer = KiranaSurfaceVariantDark,
    onSecondaryContainer = KiranaOnSurfaceVariantDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = KiranaPrimary,
    primaryContainer = KiranaPrimaryContainer,
    onPrimary = KiranaOnPrimary,
    onPrimaryContainer = KiranaOnPrimaryContainer,
    background = KiranaBackground,
    onBackground = KiranaOnBackground,
    surface = KiranaSurface,
    onSurface = KiranaOnSurface,
    surfaceVariant = KiranaSurfaceVariant,
    onSurfaceVariant = KiranaOnSurfaceVariant,
    outline = KiranaOutline,
    secondary = KiranaPrimary,
    secondaryContainer = KiranaSecondaryContainer,
    onSecondaryContainer = KiranaOnSecondaryContainer
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
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

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
