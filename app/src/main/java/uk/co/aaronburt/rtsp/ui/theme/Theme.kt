package uk.co.aaronburt.rtsp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFB224)
val AmberContainer = Color(0xFF3A2E12)
val AmberBorder = Color(0xFF5A461A)
val OnAmber = Color(0xFF1A1305)
val Danger = Color(0xFFFF5D52)
val DangerText = Color(0xFFFF7A70)
val DangerContainer = Color(0xFF3A2224)
val OnDangerContainer = Color(0xFFFFD3CF)
val OfflineSurface = Color(0xFF1E1415)
val OfflineVideo = Color(0xFF241617)
val RecordingContainer = Color(0xFF2A1617)
val RecordingText = Color(0xFFFF9A92)
val Success = Color(0xFF3DD68C)
val StatusPending = Color(0xFFFF9500)
val Scrim = Color(0x99000000)

private val Ink = Color(0xFF0E1012)
private val Raised = Color(0xFF16191C)
private val RaisedHigh = Color(0xFF1D2125)
private val RaisedHighest = Color(0xFF2A2F35)
private val Rail = Color(0xFF121518)
private val Outline = Color(0xFF2E343A)
private val Divider = Color(0xFF23282D)
private val OnInk = Color(0xFFECEEF0)
private val Muted = Color(0xFFA1A8B0)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = AmberContainer,
    onPrimaryContainer = Amber,
    secondary = Amber,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = Amber,
    tertiary = Success,
    onTertiary = OnAmber,
    error = Danger,
    onError = Color.Black,
    errorContainer = DangerContainer,
    onErrorContainer = OnDangerContainer,
    background = Ink,
    onBackground = OnInk,
    surface = Ink,
    onSurface = OnInk,
    surfaceVariant = Raised,
    onSurfaceVariant = Muted,
    surfaceTint = Amber,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Rail,
    surfaceContainer = Raised,
    surfaceContainerHigh = RaisedHigh,
    surfaceContainerHighest = RaisedHighest,
    outline = Outline,
    outlineVariant = Divider,
    scrim = Color.Black,
)

@Composable
fun RtspTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = RtspTypography,
        content = content,
    )
}
