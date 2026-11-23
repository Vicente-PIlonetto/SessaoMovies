package dev.viko.sessao.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.viko.sessao.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Entrada em cascata: o item sobe e aparece com um pequeno atraso pelo [index].
 * Toca uma vez por item (sobrevive à rolagem), então rolar de volta não repete.
 */
fun Modifier.enterAnimation(index: Int = 0, distance: Dp = 28.dp): Modifier =
    // Só a primeira tela anima; itens que aparecem ao rolar entram parados (rolar fica leve).
    if (index > 12) this else enterAnimationImpl(index, distance)

private fun Modifier.enterAnimationImpl(index: Int, distance: Dp): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    val dist = with(LocalDensity.current) { distance.toPx() }
    LaunchedEffect(Unit) {
        if (!played) {
            delay((index.coerceIn(0, 12) * 35L))
            // Mola macia com um leve "assentar": sobe, cresce e aparece juntos.
            progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 240f))
            played = true
        }
    }
    graphicsLayer {
        val v = progress.value
        // Opacidade chega antes do movimento terminar, para não parecer atrasado.
        alpha = (v * 1.6f).coerceIn(0f, 1f)
        translationY = (1f - v) * dist
        val sc = 0.94f + 0.06f * v
        scaleX = sc; scaleY = sc
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        // Sem camada fora da tela por item: a opacidade vai direto em cada desenho.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
    }
}

/** Brilho passando, para os esqueletos de carregamento. */
fun Modifier.shimmer(): Modifier = composed {
    val p = AppTheme.palette
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val base = p.surfaceAlt
    val hi = if (p.background.red + p.background.green < 0.6f) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f)
    drawWithContent {
        drawRect(base)
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, hi, Color.Transparent),
                start = Offset(w * x - w * 0.5f, 0f),
                end = Offset(w * x + w * 0.5f, size.height),
            ),
        )
    }
}

@Composable
fun SkeletonBox(width: Dp?, height: Dp, modifier: Modifier = Modifier, radius: Dp = 6.dp) {
    Box(
        modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height).clip(RoundedCornerShape(radius)).shimmer(),
    )
}

/** Esqueleto de linha da lista. */
@Composable
fun SkeletonRows(count: Int = 6) {
    Column {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).enterAnimation(i), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(44.dp).aspectRatio(2f / 3f).clip(CoverShape).shimmer())
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(null, 14.dp, Modifier.padding(end = 40.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(90.dp, 10.dp)
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(null, 4.dp, radius = 2.dp)
                }
            }
        }
    }
}

/** Esqueleto de fileira de capas. */
@Composable
fun SkeletonPosterRow(count: Int = 4) {
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) { i ->
            Column(Modifier.width(112.dp).enterAnimation(i)) {
                Box(Modifier.width(112.dp).aspectRatio(2f / 3f).clip(CoverShape).shimmer())
                Spacer(Modifier.height(8.dp))
                SkeletonBox(null, 10.dp)
                Spacer(Modifier.height(6.dp))
                SkeletonBox(60.dp, 8.dp)
            }
        }
    }
}

/** Barra de progresso fina que enche com mola. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val p = AppTheme.palette
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = 120f), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(p.divider)) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(RoundedCornerShape(height)).background(color))
    }
}

/**
 * Brilho da cor da obra que atravessa a linha quando o progresso aumenta.
 * [trigger] muda a cada +1.
 */
fun Modifier.progressGlow(trigger: Int, color: Color): Modifier = composed {
    val sweep = remember { Animatable(0f) }
    var last by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger > last) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        last = trigger
    }
    drawWithContent {
        val v = sweep.value
        if (v in 0.001f..0.999f) {
            val w = size.width
            val cx = w * (v * 1.4f - 0.2f)
            drawRect(color.copy(alpha = 0.10f * (1f - v)))
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, color.copy(alpha = 0.28f), Color.Transparent),
                    startX = cx - w * 0.25f, endX = cx + w * 0.25f,
                ),
            )
        }
        drawContent()
    }
}

/** Escala que "pula" quando [key] muda (ex.: número de progresso). */
fun Modifier.bounceOn(key: Any?): Modifier = composed {
    val s = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) { first = false; return@LaunchedEffect }
        s.snapTo(1.25f)
        s.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
    }
    graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/** Indicador de puxar-para-atualizar: um anel que enche com o puxão e gira ao carregar. */
@Composable
fun RefreshIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val frac = state.distanceFraction
    if (!refreshing && frac <= 0f) return
    val t = rememberInfiniteTransition(label = "spin")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "rot")
    val sweepAnim = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) launch {
            while (true) { sweepAnim.animateTo(280f, tween(600)); sweepAnim.animateTo(40f, tween(600)) }
        }
    }
    Box(
        modifier.padding(top = 8.dp).graphicsLayer {
            translationY = (if (refreshing) 1f else frac.coerceAtMost(1.2f)) * 60.dp.toPx() - 30.dp.toPx()
            alpha = if (refreshing) 1f else frac.coerceIn(0f, 1f)
            val sc = if (refreshing) 1f else 0.6f + 0.4f * frac.coerceIn(0f, 1f)
            scaleX = sc; scaleY = sc
        }.size(40.dp).clip(RoundedCornerShape(20.dp)).background(p.surfaceAlt),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            if (refreshing) {
                drawArc(p.accent, rot, sweepAnim.value.coerceAtLeast(30f), false, style = stroke)
            } else {
                drawArc(p.accent, -90f, 360f * frac.coerceIn(0f, 1f), false, style = stroke)
            }
        }
    }
}

/** Fundo de destaque suave (para linhas com episódio novo). */
fun Modifier.softHighlight(on: Boolean, color: Color): Modifier =
    if (on) this.background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.13f), Color.Transparent))) else this

@Composable
fun animateTint(target: Color, initial: Color): Color {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.snapTo(0f); a.animateTo(1f, tween(500)) }
    return androidx.compose.ui.graphics.lerp(initial, target, a.value)
}

@Suppress("unused")
private fun sz(w: Float, h: Float) = Size(w, h)

/** Puxar para atualizar com o indicador do app. */
@Composable
fun AppRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    // O anel só aparece (e só anima) quando a pessoa puxou. Sincronizações automáticas
    // rodam em silêncio: um indicador girando sem parar custava quadros o tempo todo.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) { if (!isRefreshing) pulled = false }
    val shown = pulled && isRefreshing
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = shown, onRefresh = { pulled = true; onRefresh() }, modifier = modifier, state = state,
        indicator = { RefreshIndicator(state, shown, Modifier.align(Alignment.TopCenter)) },
        content = content,
    )
}

/** Esqueleto de atividade/comentário (avatar + linhas). */
@Composable
fun SkeletonActivities(count: Int = 5) {
    Column {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).enterAnimation(i)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(100.dp, 12.dp)
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(null, 10.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(null, 10.dp, Modifier.padding(end = 60.dp))
                }
            }
        }
    }
}

/** Esqueleto do Detalhe: banner, capa, título e cartão de ações. */
@Composable
fun SkeletonDetail() {
    Column {
        Box(Modifier.fillMaxWidth().height(330.dp).shimmer()) {
            Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.width(118.dp).aspectRatio(2f / 3f).clip(CoverShape).background(AppTheme.palette.divider))
                Spacer(Modifier.width(16.dp))
                Column {
                    Box(Modifier.width(160.dp).height(18.dp).clip(RoundedCornerShape(6.dp)).background(AppTheme.palette.divider))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(100.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(AppTheme.palette.divider))
                }
            }
        }
        SkeletonBox(null, 120.dp, Modifier.padding(16.dp), radius = 18.dp)
        SkeletonRows(3)
    }
}

/** Esqueleto do cabeçalho de perfil. */
@Composable
fun SkeletonProfile() {
    Column {
        Box(Modifier.fillMaxWidth().height(190.dp).shimmer())
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).shimmer())
            Spacer(Modifier.width(14.dp))
            SkeletonBox(140.dp, 18.dp)
        }
        SkeletonActivities(4)
    }
}
