package co.zw.nissangtr.catalogapk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Slate900 = Color(0xFF0F1419)
private val Slate800 = Color(0xFF1A2332)
private val Slate700 = Color(0xFF243447)
private val AmberAccent = Color(0xFFFFB300)
private val AmberMuted = Color(0xFFFFCA28)
private val TextPrimary = Color(0xFFE8EDF4)
private val TextSecondary = Color(0xFF94A3B8)

private val CatalogColorScheme = darkColorScheme(
    primary = AmberAccent,
    onPrimary = Slate900,
    secondary = AmberMuted,
    onSecondary = Slate900,
    background = Slate900,
    onBackground = TextPrimary,
    surface = Slate800,
    onSurface = TextPrimary,
    surfaceVariant = Slate700,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3D5166),
)

@Composable
fun CatalogApkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CatalogColorScheme,
        content = content,
    )
}
