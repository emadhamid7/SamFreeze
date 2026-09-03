package com.samfreeze.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// One UI–style palette. Deliberately NOT using Android 12+ dynamic
// (wallpaper-derived) color — SamFreeze keeps a fixed Samsung-blue identity
// regardless of the device's wallpaper.
private val LightColors = lightColorScheme(
    primary = OneUIBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF00214F),
    secondary = OneUIBlue,
    tertiary = FrozenBlue,
    tertiaryContainer = Color(0xFFD7E4FF),
    onTertiaryContainer = Color(0xFF00214F),
    error = RedError,
    background = SurfaceLight,
    surface = RowLight,
    surfaceVariant = Color(0xFFECECEE),
    onSurfaceVariant = Color(0xFF55565C),
    outline = Color(0xFF86878D)
)

private val DarkColors = darkColorScheme(
    primary = OneUIBlueDark,
    onPrimary = Color(0xFF00214F),
    primaryContainer = Color(0xFF17335E),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = OneUIBlueDark,
    tertiary = FrozenBlueDark,
    tertiaryContainer = Color(0xFF17335E),
    onTertiaryContainer = Color(0xFFD7E4FF),
    error = RedErrorDark,
    background = SurfaceDark,
    surface = RowDark,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFB8B8BD),
    outline = Color(0xFF7A7A7E)
)

enum class AppTheme { SYSTEM, LIGHT, DARK }

/** Reads the persisted theme preference and maps it to [AppTheme], reactively. */
@Composable
fun rememberAppTheme(preferencesRepository: com.samfreeze.app.data.PreferencesRepository): AppTheme {
    val themePref by preferencesRepository.theme.collectAsStateWithLifecycle(initialValue = "system")
    return when (themePref) {
        "light" -> AppTheme.LIGHT
        "dark" -> AppTheme.DARK
        else -> AppTheme.SYSTEM
    }
}

@Composable
fun SamFreezeTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appTheme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    val colorScheme = if (useDark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
