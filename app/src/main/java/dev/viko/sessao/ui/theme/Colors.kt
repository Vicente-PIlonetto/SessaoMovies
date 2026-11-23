package dev.viko.sessao.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
import dev.viko.sessao.domain.WatchStatus

/** Cor de cada status, ajustada para claro e escuro. "Em dia" tem um verde-água próprio. */
fun statusColor(status: WatchStatus, dark: Boolean): Color = Color(
    when (status) {
        WatchStatus.WATCHING -> if (dark) 0xFF4FB3F0 else 0xFF1C79B8
        WatchStatus.CAUGHT_UP -> if (dark) 0xFF4FD1BE else 0xFF12806F
        WatchStatus.PLANNING -> if (dark) 0xFFF2A65E else 0xFFB8641C
        WatchStatus.COMPLETED -> if (dark) 0xFF6FD07A else 0xFF2E8A3B
        WatchStatus.PAUSED -> if (dark) 0xFFF0CC4F else 0xFF9A7A0B
        WatchStatus.DROPPED -> if (dark) 0xFFEC6F82 else 0xFFB83248
        WatchStatus.REWATCHING -> if (dark) 0xFFA98AF2 else 0xFF6B45C2
    },
)

/**
 * Cor do pôster (extraída com a Palette) ajustada para ter contraste com o fundo:
 * clara no tema escuro, escura no tema claro, e com saturação mínima.
 */
fun coverTint(argb: Int?, dark: Boolean, fallback: Color): Color {
    argb ?: return fallback
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    if (hsl[1] < 0.12f) return fallback // pôster quase cinza: usa o destaque do tema
    hsl[1] = hsl[1].coerceIn(0.40f, 0.85f)
    hsl[2] = if (dark) hsl[2].coerceIn(0.62f, 0.75f) else hsl[2].coerceIn(0.32f, 0.42f)
    return Color(ColorUtils.HSLToColor(hsl))
}

val Palette.isDark: Boolean get() = background.luminance() < 0.5f

@Composable
fun rememberCoverTint(argb: Int?): Color {
    val p = AppTheme.palette
    return remember(argb, p.isDark, p.accent) { coverTint(argb, p.isDark, p.accent) }
}

/** Paleta da tela de um título: destaque = cor do pôster, fundo levemente tingido. */
fun Palette.tinted(tint: Color, amount: Float = 0.09f): Palette = copy(
    accent = tint,
    background = tint.copy(alpha = amount).compositeOver(background),
    surfaceAlt = tint.copy(alpha = amount + 0.07f).compositeOver(background),
    divider = tint.copy(alpha = 0.14f).compositeOver(divider),
)

@Composable
fun statusColor(status: WatchStatus): Color = statusColor(status, AppTheme.palette.isDark)
