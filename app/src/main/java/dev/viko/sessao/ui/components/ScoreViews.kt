package dev.viko.sessao.ui.components

import dev.viko.sessao.i18n.t
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.viko.sessao.domain.score.ScoreDisplay
import dev.viko.sessao.domain.score.ScoreSystem
import dev.viko.sessao.domain.score.Scores
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono

/**
 * Cor de cada carinha, da pior para a melhor: vermelho, laranja, amarelo, branco, ciano, azul, verde.
 * No tema claro o branco vira cinza e o amarelo escurece, para aparecerem no fundo claro.
 */
@Composable
fun faceColor(level: Int): Color {
    val dark = AppTheme.palette.background.luminance() < 0.5f
    val l = level.coerceIn(1, 7)
    val (d, li) = when (l) {
        1 -> 0xFFE5484D to 0xFFC62828
        2 -> 0xFFF28C38 to 0xFFD9480F
        3 -> 0xFFF5D547 to 0xFFB08900
        4 -> 0xFFF2F2F2 to 0xFF8A8A8A
        5 -> 0xFF4FD1E0 to 0xFF0E8FA0
        6 -> 0xFF4F8DF5 to 0xFF1E5FC7
        else -> 0xFF4CC95A to 0xFF2E8B3D
    }
    return Color(if (dark) d else li)
}

/**
 * Carinha de traço fino, desenhada no estilo do app. [level] de 1 (Detestei) a 7 (Adorei).
 */
@Composable
fun FaceIcon(level: Int, color: Color, size: Dp, modifier: Modifier = Modifier, label: String? = null) {
    Canvas(modifier.size(size).semantics { if (label != null) contentDescription = label }) {
        val s = this.size.minDimension
        val w = s * 0.065f
        val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(color, radius = s * 0.45f - w / 2, center = center, style = stroke)

        val eyeY = s * 0.41f
        val lx = s * 0.355f
        val rx = s * 0.645f
        if (level == 7) {
            // Olhos fechados de alegria (arcos para cima).
            for (x in listOf(lx, rx)) {
                val p = Path().apply {
                    moveTo(x - s * 0.07f, eyeY + s * 0.03f)
                    quadraticTo(x, eyeY - s * 0.07f, x + s * 0.07f, eyeY + s * 0.03f)
                }
                drawPath(p, color, style = stroke)
            }
        } else {
            drawCircle(color, radius = w * 0.85f, center = Offset(lx, eyeY))
            drawCircle(color, radius = w * 0.85f, center = Offset(rx, eyeY))
        }
        if (level == 1) {
            // Sobrancelhas franzidas.
            drawLine(color, Offset(lx - s * 0.08f, eyeY - s * 0.12f), Offset(lx + s * 0.06f, eyeY - s * 0.07f), w, StrokeCap.Round)
            drawLine(color, Offset(rx + s * 0.08f, eyeY - s * 0.12f), Offset(rx - s * 0.06f, eyeY - s * 0.07f), w, StrokeCap.Round)
        }

        val mx0 = s * 0.32f
        val mx1 = s * 0.68f
        val my = s * 0.64f
        if (level == 7) {
            // Boca aberta em "D".
            val p = Path().apply {
                moveTo(mx0, my - s * 0.02f)
                lineTo(mx1, my - s * 0.02f)
                quadraticTo(s * 0.5f, my + s * 0.26f, mx0, my - s * 0.02f)
                close()
            }
            drawPath(p, color, style = stroke)
        } else {
            val curve = when (level) { 6 -> 1f; 5 -> 0.55f; 4 -> 0f; 3 -> -0.45f; else -> -0.9f }
            val y = if (curve < 0) my + s * 0.06f else my
            val p = Path().apply {
                moveTo(mx0, y)
                quadraticTo(s * 0.5f, y + curve * s * 0.2f, mx1, y)
            }
            drawPath(p, color, style = stroke)
        }
    }
}

@Composable
fun StarsView(halves: Int, size: Dp, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = t("{0} estrelas", Scores.starsText(halves)) }) {
        for (i in 1..5) {
            val icon = when {
                halves >= i * 2 -> Icons.Filled.Star
                halves == i * 2 - 1 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarOutline
            }
            Icon(icon, null, tint = color, modifier = Modifier.size(size))
        }
    }
}

enum class ScoreSize(val primary: Int, val secondary: Int, val icon: Dp) {
    Small(14, 11, 16.dp), Normal(17, 12, 20.dp), Large(28, 15, 32.dp)
}

/** Uma nota num sistema, sozinha. */
@Composable
fun ScoreGlyph(raw: Int, system: ScoreSystem, fontSize: Int, iconSize: Dp, color: Color) {
    val p = AppTheme.palette
    when (val d = Scores.display(raw, system)) {
        ScoreDisplay.None -> Text("–", style = Mono.style(fontSize), color = p.textTertiary)
        is ScoreDisplay.Text -> Text(
            d.text,
            style = Mono.style(fontSize, FontWeight.Medium),
            color = color,
        )
        is ScoreDisplay.Stars -> StarsView(d.halves, iconSize * 0.8f, color)
        is ScoreDisplay.Face -> FaceIcon(d.level, faceColor(d.level), iconSize, label = d.label)
    }
}

/** Principal maior + secundário menor ao lado, em mono. Ex.: "A · 89". */
@Composable
fun ScoreView(
    raw: Int,
    primary: ScoreSystem,
    secondary: ScoreSystem?,
    size: ScoreSize = ScoreSize.Normal,
    modifier: Modifier = Modifier,
) {
    val p = AppTheme.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ScoreGlyph(raw, primary, size.primary, size.icon, p.text)
        if (secondary != null && secondary != primary && raw > 0) {
            Text(" · ", style = Mono.style(size.secondary), color = p.textTertiary)
            ScoreGlyph(raw, secondary, size.secondary, size.icon * 0.7f, p.textSecondary)
        }
    }
}

@Composable
fun SpacerW(dp: Dp) = Spacer(Modifier.width(dp))
