package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
}

private val StudyPulseLightColors =
  lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = IndigoOnPrimaryLight,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = IndigoOnContainerLight,
    secondary = MintSecondaryLight,
    onSecondary = MintOnSecondaryLight,
    secondaryContainer = MintContainerLight,
    onSecondaryContainer = MintOnContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = AmberOnTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberOnTertiaryContainerLight,
    background = SurfaceBackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceCardLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceCardLight,
    surfaceContainerLow = ColorTokens.SoftCardLight,
    surfaceContainer = SurfaceVariantLight,
    surfaceContainerHigh = ColorTokens.ElevatedLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
  )

private val StudyPulseDarkColors =
  darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = IndigoOnPrimaryDark,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = IndigoOnContainerDark,
    secondary = MintSecondaryDark,
    onSecondary = MintOnSecondaryDark,
    secondaryContainer = MintContainerDark,
    onSecondaryContainer = MintOnContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = AmberOnTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberOnTertiaryContainerDark,
    background = SurfaceBackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceCardDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceBackgroundDark,
    surfaceContainerLow = SurfaceCardDark,
    surfaceContainer = SurfaceVariantDark,
    surfaceContainerHigh = ColorTokens.ElevatedDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
  )

private object ColorTokens {
  val SoftCardLight = androidx.compose.ui.graphics.Color(0xFFFAFBFD)
  val ElevatedLight = androidx.compose.ui.graphics.Color(0xFFE3E7F4)
  val ElevatedDark = androidx.compose.ui.graphics.Color(0xFF283056)
}

val StudyPulseShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun StudyPulseTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val darkTheme =
    when (themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }
  val colorScheme = if (darkTheme) StudyPulseDarkColors else StudyPulseLightColors

  MaterialTheme(
    colorScheme = colorScheme,
    typography = StudyPulseTypography,
    shapes = StudyPulseShapes,
    content = content,
  )
}
