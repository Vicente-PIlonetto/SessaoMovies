package dev.viko.sessao.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.viko.sessao.data.settings.AnimatedBg
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Uma partícula com parâmetros fixos; a posição sai do tempo. */
private class Particle(r: Random) {
    val x = r.nextFloat()
    val y = r.nextFloat()
    val speed = 0.4f + r.nextFloat() * 0.8f
    val size = 0.5f + r.nextFloat()
    val phase = r.nextFloat() * 2f * PI.toFloat()
    val sway = 0.5f + r.nextFloat()
    val spin = (r.nextFloat() - 0.5f) * 4f
    val tone = r.nextInt(3)
}

/**
 * Fundo animado do perfil. O tempo é lido só no desenho, então animar não recompõe nada.
 * [colors] = cores do perfil (1 a 5).
 */
@Composable
fun AnimatedBackground(style: AnimatedBg, colors: List<Color>, modifier: Modifier = Modifier) {
    if (style == AnimatedBg.NONE) return
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(style) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }
    val particles = remember(style) { val r = Random(7); List(if (style == AnimatedBg.STARS) 70 else 28) { Particle(r) } }
    val pal = colors.ifEmpty { listOf(Color(0xFF3DB4F2)) }
    fun c(i: Int) = pal[i % pal.size]

    Canvas(modifier) {
        val t = time.floatValue
        when (style) {
            AnimatedBg.AURORA -> aurora(t, pal)
            AnimatedBg.PETALS -> particles.forEach { petal(it, t, if (pal.size > 1) c(it.tone) else Color(0xFFF7A8C4)) }
            AnimatedBg.STARS -> stars(particles, t)
            AnimatedBg.WAVES -> waves(t, pal)
            AnimatedBg.BUBBLES -> particles.forEach { bubble(it, t, c(it.tone)) }
            AnimatedBg.EMBERS -> particles.forEach { ember(it, t) }
            AnimatedBg.NONE -> {}
        }
    }
}

private fun DrawScope.aurora(t: Float, pal: List<Color>) {
    val w = size.width; val h = size.height
    for (i in 0 until 3) {
        val col = pal[i % pal.size]
        val cx = w * (0.5f + 0.4f * sin(t * (0.21f + i * 0.07f) + i * 2f))
        val cy = h * (0.45f + 0.35f * cos(t * (0.17f + i * 0.05f) + i))
        val r = w * (0.55f + 0.1f * sin(t * 0.3f + i))
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
    }
}

private fun DrawScope.petal(p: Particle, t: Float, color: Color) {
    val w = size.width; val h = size.height
    val fall = (p.y + t * 0.06f * p.speed) % 1.15f - 0.08f
    val x = w * p.x + sin(t * p.sway + p.phase) * 26f
    val y = h * fall
    val s = 7f * p.size + 4f
    rotate(degrees = t * 40f * p.spin + p.phase * 57f, pivot = Offset(x, y)) {
        drawOval(color.copy(alpha = 0.85f), Offset(x - s, y - s * 0.55f), Size(s * 2f, s * 1.1f))
    }
}

private fun DrawScope.stars(ps: List<Particle>, t: Float) {
    val w = size.width; val h = size.height
    ps.forEach { p ->
        val a = 0.25f + 0.75f * abs(sin(t * p.speed * 1.4f + p.phase))
        drawCircle(Color.White.copy(alpha = a), 1.2f + p.size * 1.6f, Offset(w * p.x, h * p.y))
    }
    // Estrela cadente a cada ~6 s.
    val cycle = (t % 6f) / 6f
    if (cycle < 0.18f) {
        val k = cycle / 0.18f
        val sx = w * (0.15f + 0.7f * k); val sy = h * (0.1f + 0.35f * k)
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f)), Offset(sx - 90f, sy - 45f), Offset(sx, sy)),
            Offset(sx - 90f, sy - 45f), Offset(sx, sy), strokeWidth = 3f,
        )
    }
}

private fun DrawScope.waves(t: Float, pal: List<Color>) {
    val w = size.width; val h = size.height
    for (i in 0 until 3) {
        val path = Path()
        val base = h * (0.55f + i * 0.13f)
        val amp = h * (0.06f + 0.02f * i)
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w + 8f) {
            path.lineTo(x, base + amp * sin(x / w * 2f * PI.toFloat() * (1.2f + i * 0.3f) + t * (0.8f + i * 0.35f)))
            x += 8f
        }
        path.lineTo(w, h); path.close()
        drawPath(path, pal[i % pal.size].copy(alpha = 0.28f))
    }
}

private fun DrawScope.bubble(p: Particle, t: Float, color: Color) {
    val w = size.width; val h = size.height
    val rise = 1.1f - ((p.y + t * 0.05f * p.speed) % 1.2f)
    val x = w * p.x + sin(t * p.sway + p.phase) * 14f
    val r = 5f + p.size * 9f
    drawCircle(color.copy(alpha = 0.45f), r, Offset(x, h * rise), style = Stroke(2f))
    drawCircle(Color.White.copy(alpha = 0.35f), r * 0.25f, Offset(x - r * 0.35f, h * rise - r * 0.35f))
}

private fun DrawScope.ember(p: Particle, t: Float) {
    val w = size.width; val h = size.height
    val life = (p.y + t * 0.08f * p.speed) % 1f
    val y = h * (1f - life)
    val x = w * p.x + sin(t * 2f * p.sway + p.phase) * 10f
    val flicker = 0.5f + 0.5f * abs(sin(t * 8f * p.speed + p.phase))
    val col = if (p.tone == 0) Color(0xFFFFC371) else if (p.tone == 1) Color(0xFFFF7E3F) else Color(0xFFFF4E2F)
    drawCircle(col.copy(alpha = (1f - life) * flicker), 1.5f + p.size * 2.5f, Offset(x, y))
}
