package com.savvasg.offlinenotes.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = InkBlue40,
    onPrimary = Color.White,
    primaryContainer = InkBlue90,
    onPrimaryContainer = InkBlue10,
    secondary = Sand40,
    onSecondary = Color.White,
    secondaryContainer = Sand90,
    onSecondaryContainer = Sand10,
    tertiary = Sage40,
    onTertiary = Color.White,
    error = Clay40,
    onError = Color.White,
    errorContainer = Clay90,
    onErrorContainer = Color(0xFF3B120C),
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = PaperRaised,
    surfaceContainer = Paper,
    surfaceContainerHigh = PaperSunken,
    outline = PaperOutline,
    outlineVariant = PaperSunken,
)

private val DarkColors = darkColorScheme(
    primary = InkBlue80,
    onPrimary = InkBlue10,
    primaryContainer = InkBlue20,
    onPrimaryContainer = InkBlue90,
    secondary = Sand80,
    onSecondary = Sand10,
    secondaryContainer = Color(0xFF463A27),
    onSecondaryContainer = Sand90,
    tertiary = Sage80,
    onTertiary = Color(0xFF10281C),
    error = Clay80,
    onError = Color(0xFF52140C),
    errorContainer = Color(0xFF6B2A20),
    onErrorContainer = Clay90,
    background = Night,
    onBackground = Bone,
    surface = NightRaised,
    onSurface = Bone,
    surfaceVariant = NightSunken,
    onSurfaceVariant = BoneMuted,
    surfaceContainerLowest = Night,
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightSunken,
    outline = NightOutline,
    outlineVariant = NightSunken,
)

class StatusColors internal constructor(
    val synced: Color,
    val pending: Color,
    val conflict: Color,
    val rejected: Color,
)

private val LocalStatusColors: ProvidableCompositionLocal<StatusColors> =
    staticCompositionLocalOf {
        StatusColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
    }

object OfflineNotesTheme {
    val statusColors: StatusColors
        @Composable get() = LocalStatusColors.current
}

@Composable
fun OfflineNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusColors = StatusColors(
        synced = colorScheme.tertiary,
        pending = colorScheme.secondary,
        conflict = colorScheme.error,
        rejected = colorScheme.error,
    )
    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OfflineNotesTypography,
            shapes = OfflineNotesShapes,
            content = content,
        )
    }
}
