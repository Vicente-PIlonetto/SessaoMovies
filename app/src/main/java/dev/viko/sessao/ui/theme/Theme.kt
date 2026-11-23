package dev.viko.sessao.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.viko.sessao.R
import dev.viko.sessao.data.settings.ThemeMode
import dev.viko.sessao.data.settings.Accent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** Cores além do esquema do Material: uma cor de destaque e tons de cinza quente. */
@Immutable
data class Palette(
    val accent: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val surfaceAlt: Color,
    val background: Color,
)

private val LightPalette = Palette(
    accent = Color(0xFF94650F),
    text = Color(0xFF1F1D1A),
    textSecondary = Color(0xFF6E6961),
    textTertiary = Color(0xFF9C968C),
    divider = Color(0xFFE2DDD3),
    surfaceAlt = Color(0xFFEDEAE3),
    background = Color(0xFFF6F4EF),
)

private val DarkPalette = Palette(
    accent = Color(0xFFE2B252),
    text = Color(0xFFECE8E1),
    textSecondary = Color(0xFFA59F95),
    textTertiary = Color(0xFF746F67),
    divider = Color(0xFF2A2826),
    surfaceAlt = Color(0xFF1F1E1D),
    background = Color(0xFF161616),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private fun scheme(p: Palette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val onAccent = if (dark) Color(0xFF161616) else Color.White
    return base.copy(
        primary = p.accent, onPrimary = onAccent,
        primaryContainer = p.surfaceAlt, onPrimaryContainer = p.text,
        secondary = p.textSecondary, onSecondary = p.background,
        secondaryContainer = p.surfaceAlt, onSecondaryContainer = p.text,
        tertiary = p.textSecondary, onTertiary = p.background,
        tertiaryContainer = p.surfaceAlt, onTertiaryContainer = p.text,
        background = p.background, onBackground = p.text,
        surface = p.background, onSurface = p.text,
        surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.textSecondary,
        surfaceTint = p.background,
        surfaceDim = p.background, surfaceBright = p.background,
        surfaceContainerLowest = p.background, surfaceContainerLow = p.background,
        surfaceContainer = p.background, surfaceContainerHigh = p.surfaceAlt, surfaceContainerHighest = p.surfaceAlt,
        inverseSurface = p.text, inverseOnSurface = p.background, inversePrimary = p.accent,
        outline = p.textTertiary, outlineVariant = p.divider,
        error = if (dark) Color(0xFFE08A7E) else Color(0xFFA33B2E),
        scrim = Color.Black,
    )
}

private fun typography(): Typography {
    fun s(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.35).toInt()) =
        TextStyle(fontFamily = PlexSans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
    return Typography(
        displaySmall = s(30, FontWeight.SemiBold),
        headlineLarge = s(28, FontWeight.SemiBold),
        headlineMedium = s(24, FontWeight.SemiBold),
        headlineSmall = s(21, FontWeight.SemiBold),
        titleLarge = s(20, FontWeight.SemiBold),
        titleMedium = s(16, FontWeight.SemiBold),
        titleSmall = s(14, FontWeight.SemiBold),
        bodyLarge = s(16, line = 24),
        bodyMedium = s(14, line = 20),
        bodySmall = s(12, line = 16),
        labelLarge = s(14, FontWeight.Medium),
        labelMedium = s(12, FontWeight.Medium),
        labelSmall = s(11, FontWeight.Medium),
    )
}

/** Números, progresso e notas: IBM Plex Mono com algarismos tabulares. */
object Mono {
    fun style(size: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = PlexMono, fontWeight = weight, fontSize = size.sp, fontFeatureSettings = "tnum")
}

/** Paleta a partir do Material You (cores do papel de parede). */
private fun fromDynamic(d: ColorScheme, dark: Boolean) = Palette(
    accent = d.primary,
    text = d.onSurface,
    textSecondary = d.onSurfaceVariant,
    textTertiary = d.outline,
    divider = d.outlineVariant.copy(alpha = if (dark) 0.6f else 0.8f),
    surfaceAlt = d.surfaceContainerHigh,
    background = d.surface,
)

fun buildPalette(
    context: android.content.Context,
    dark: Boolean,
    accent: Accent,
    materialYou: Boolean,
    oled: Boolean,
): Palette {
    var p = if (materialYou && android.os.Build.VERSION.SDK_INT >= 31) {
        fromDynamic(if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context), dark)
    } else {
        (if (dark) DarkPalette else LightPalette).copy(accent = Color(if (dark) accent.dark else accent.light))
    }
    if (dark && oled) p = p.copy(background = Color.Black, surfaceAlt = Color(0xFF121212), divider = Color(0xFF222222))
    return p
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.OLED -> true
}

@Composable
fun SessaoTheme(
    mode: ThemeMode,
    accent: Accent = Accent.AMBAR,
    materialYou: Boolean = false,
    oled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDark(mode)
    val ctx = LocalContext.current
    val black = mode == ThemeMode.OLED || (dark && oled)
    val target = remember(dark, accent, materialYou, black) { buildPalette(ctx, dark, accent, materialYou, black) }
    // Troca de tema/cor animada.
    val spec = tween<Color>(350)
    val p = target.copy(
        accent = animateColorAsState(target.accent, spec, label = "accent").value,
        text = animateColorAsState(target.text, spec, label = "text").value,
        textSecondary = animateColorAsState(target.textSecondary, spec, label = "t2").value,
        textTertiary = animateColorAsState(target.textTertiary, spec, label = "t3").value,
        divider = animateColorAsState(target.divider, spec, label = "div").value,
        surfaceAlt = animateColorAsState(target.surfaceAlt, spec, label = "alt").value,
        background = animateColorAsState(target.background, spec, label = "bg").value,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme(p, dark), typography = typography()) {
            // Cor padrão dos textos e ícones (antes vinha do Scaffold).
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides p.text, content = content)
        }
    }
}

object AppTheme {
    val palette: Palette @Composable get() = LocalPalette.current
}
