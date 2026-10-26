package pt.vereda.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colours from docs/design/README.md. */
object VeredaColors {
    val Laurel = Color(0xFF1F5F3F)
    val LaurelTint = Color(0xFFE4EFE7)
    val Ink = Color(0xFF17201B)
    val InkSecondary = Color(0xFF4E5A53)
    val Background = Color(0xFFF6F4EE)
    val CardBorder = Color(0xFFECE9E1)
    val Record = Color(0xFFC2410C)

    val OpenLine = Color(0xFF2E9E5B)
    val OpenText = Color(0xFF1B7A44)
    val OpenTint = Color(0xFFE3F3EA)
    val PartialLine = Color(0xFFE3A008)
    val PartialText = Color(0xFF8A5300)
    val PartialTint = Color(0xFFFDF0D5)
    val ClosedLine = Color(0xFFC62828)
    val ClosedText = Color(0xFFB42318)
    val ClosedTint = Color(0xFFFDE7E5)
    val UnknownLine = Color(0xFF8A8A8A)
    val UnknownText = Color(0xFF5F6368)
    val UnknownTint = Color(0xFFEEEFF1)

    val WaymarkYellow = Color(0xFFF2C200)
    val WaymarkRed = Color(0xFFD32F2F)
}

private val LightColors = lightColorScheme(
    primary = VeredaColors.Laurel,
    onPrimary = Color.White,
    primaryContainer = VeredaColors.LaurelTint,
    onPrimaryContainer = Color(0xFF14532D),
    secondaryContainer = VeredaColors.LaurelTint,
    onSecondaryContainer = Color(0xFF14532D),
    background = VeredaColors.Background,
    onBackground = VeredaColors.Ink,
    surface = Color.White,
    onSurface = VeredaColors.Ink,
    surfaceVariant = Color(0xFFF1EFE8),
    onSurfaceVariant = VeredaColors.InkSecondary,
    outline = Color(0xFFDDD8CC),
    outlineVariant = VeredaColors.CardBorder,
    error = VeredaColors.ClosedText,
)

@Composable
fun VeredaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
