package app.pickles.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.pickles.R
import androidx.compose.ui.unit.sp
import app.pickles.engine.TeamId

object PicklesColors {
    val Navy = Color(0xFF0B1426)
    val Panel = Color(0xFF14213A)
    val PanelHigh = Color(0xFF1D2E50)
    val Line = Color(0xFFFFFFFF)
    val Court = Color(0xFF2F5D8C)
    val Kitchen = Color(0xFF3A6E9F)
    val Surround = Color(0xFF3E7D4E)
    val Volt = Color(0xFFC6F432)
    val TeamA = Color(0xFFFFB300)
    val TeamB = Color(0xFF29B6F6)
    val Muted = Color(0xFF9FB0CC)
    val Danger = Color(0xFFFF6B6B)

    fun team(t: TeamId): Color = if (t == TeamId.A) TeamA else TeamB
}

private val scheme = darkColorScheme(
    primary = PicklesColors.Volt,
    onPrimary = PicklesColors.Navy,
    secondary = PicklesColors.TeamB,
    background = PicklesColors.Navy,
    onBackground = Color.White,
    surface = PicklesColors.Panel,
    onSurface = Color.White,
    surfaceVariant = PicklesColors.PanelHigh,
    onSurfaceVariant = PicklesColors.Muted,
    error = PicklesColors.Danger,
)

/** Interface font: Plus Jakarta Sans (OFL), bundled so the app stays fully offline. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

/** Scoreboard font: Barlow Condensed (OFL) for digits and the score call. */
val Scoreboard = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.Black),
)

private val typography = Typography().run {
    fun TextStyle.j() = copy(fontFamily = Jakarta)
    copy(
        displayLarge = displayLarge.j(), displayMedium = displayMedium.j(), displaySmall = displaySmall.j(),
        headlineLarge = headlineLarge.j().copy(fontWeight = FontWeight.Black),
        headlineMedium = headlineMedium.j(), headlineSmall = headlineSmall.j(),
        titleLarge = titleLarge.j().copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.j(), titleSmall = titleSmall.j(),
        bodyLarge = bodyLarge.j(), bodyMedium = bodyMedium.j(), bodySmall = bodySmall.j(),
        labelLarge = labelLarge.j().copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        labelMedium = labelMedium.j(), labelSmall = labelSmall.j(),
    )
}

val ScoreDigits = TextStyle(
    fontFamily = Scoreboard,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 52.sp,
    lineHeight = 52.sp,
    fontFeatureSettings = "tnum",
)

/** Score call ("4 – 7 – 2"): condensed, tabular figures so it doesn't jitter as numbers change. */
val CallStyle = TextStyle(fontFamily = Scoreboard, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = 0.5.sp)

@Composable
fun PicklesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Text drawn outside a Surface would otherwise default to black on the navy background.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Color.White,
            content = content,
        )
    }
}
