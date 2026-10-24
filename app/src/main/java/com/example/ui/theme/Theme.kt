package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = EmeraldPrimaryDark,
  onPrimary = EmeraldOnPrimaryDark,
  primaryContainer = EmeraldContainerDark,
  onPrimaryContainer = EmeraldOnContainerDark,
  secondary = GoldSecondaryDark,
  onSecondary = GoldOnSecondaryDark,
  secondaryContainer = GoldContainerDark,
  onSecondaryContainer = GoldOnContainerDark,
  tertiary = CyanTertiaryDark,
  background = ObsidianBackground,
  onBackground = TextPrimaryDark,
  surface = ObsidianSurface,
  onSurface = TextPrimaryDark,
  surfaceVariant = ObsidianSurfaceVariant,
  onSurfaceVariant = TextSecondaryDark,
  error = AlertCoral,
  onError = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
  primary = EmeraldPrimaryLight,
  onPrimary = EmeraldOnPrimaryLight,
  primaryContainer = EmeraldContainerLight,
  onPrimaryContainer = EmeraldOnContainerLight,
  secondary = GoldSecondaryLight,
  onSecondary = GoldOnSecondaryLight,
  secondaryContainer = GoldContainerLight,
  onSecondaryContainer = GoldOnContainerLight,
  tertiary = CyanTertiaryLight,
  background = MintBackgroundLight,
  onBackground = TextPrimaryLight,
  surface = MintSurfaceLight,
  onSurface = TextPrimaryLight,
  surfaceVariant = MintSurfaceVariantLight,
  onSurfaceVariant = TextSecondaryLight,
  error = Color(0xFFDC2626),
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
