package uk.co.aaronburt.rtsp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFB224)
val RecordRed = Color(0xFFFF5D52)
val SurfaceDark = Color(0xFF0B0D0F)
val SurfaceRaised = Color(0xFF1A1E22)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color.Black,
    error = RecordRed,
    background = SurfaceDark,
    onBackground = Color(0xFFE6E8EA),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E8EA),
    surfaceContainer = SurfaceRaised,
)

@Composable
fun RtspTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
