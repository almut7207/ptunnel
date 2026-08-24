package xyz.babyplatipus.ptunnel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Sky = Color(0xFF0EA5E9)
private val Deep = Color(0xFF0B1220)
private val Card = Color(0xFF152033)
private val OkGreen = Color(0xFF22C55E)

private val DarkColors = darkColorScheme(
    primary = Sky,
    onPrimary = Color.White,
    background = Deep,
    surface = Card,
    onSurface = Color(0xFFE5EEF7),
    secondary = OkGreen
)

private val LightColors = lightColorScheme(
    primary = Sky,
    background = Color(0xFFF3F7FB),
    surface = Color.White
)

@Composable
fun PtunnelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else DarkColors, // всегда тёмная — брендовая
        content = content
    )
}
