package dev.jiankaichen.clock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import dev.jiankaichen.clock.ui.GitHub

/** Always dark, GitHub palette. Dynamic (wallpaper) color was too pale for the owner. */
private val Scheme = darkColorScheme(
    primary = GitHub.Green3,
    onPrimary = GitHub.Bg,
    primaryContainer = GitHub.Green1,
    onPrimaryContainer = GitHub.Text,
    secondary = GitHub.Green2,
    onSecondary = GitHub.Bg,
    secondaryContainer = GitHub.Green0,
    onSecondaryContainer = GitHub.Text,
    tertiary = GitHub.Blue,
    onTertiary = GitHub.Bg,
    tertiaryContainer = GitHub.Surface,
    onTertiaryContainer = GitHub.Text,
    error = GitHub.Red,
    onError = GitHub.Bg,
    background = GitHub.Bg,
    onBackground = GitHub.Text,
    surface = GitHub.Bg,
    onSurface = GitHub.Text,
    surfaceVariant = GitHub.Surface,
    onSurfaceVariant = GitHub.Muted,
    surfaceContainer = GitHub.Surface,
    surfaceContainerLow = GitHub.Surface,
    surfaceContainerHigh = GitHub.Surface,
    surfaceContainerHighest = GitHub.Border,
    outline = GitHub.Border,
    outlineVariant = GitHub.Border,
)

@Composable
fun ClockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
