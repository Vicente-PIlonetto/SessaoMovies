package dev.viko.sessao.ui.components

import dev.viko.sessao.i18n.t
import kotlinx.coroutines.launch
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono

val CoverShape = RoundedCornerShape(8.dp)

/** Pôster 2:3 com cantos de 8dp. [color] (ARGB) pinta o fundo enquanto a imagem carrega. */
@Composable
fun Cover(url: String?, width: Dp, modifier: Modifier = Modifier, color: Int? = null, sharedKey: String? = null) {
    val p = AppTheme.palette
    val bg = remember(color, p) { color?.let { Color(it).copy(alpha = 0.25f) } ?: p.surfaceAlt }
    Box(modifier.sharedCover(sharedKey).width(width).aspectRatio(2f / 3f).clip(CoverShape).background(bg)) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
fun NewDot(modifier: Modifier = Modifier) {
    Box(modifier.size(7.dp).clip(CircleShape).background(AppTheme.palette.accent))
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 0.5.dp, color = AppTheme.palette.divider)

/** Vibração leve ao marcar progresso. */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
}

/** "+1" em pílula na cor do pôster. O toque longo abre o seletor (episódio exato). */
@Composable
fun PlusOneButton(
    enabled: Boolean,
    bounceKey: Any?,
    onIncrement: () -> Unit,
    onLongPress: () -> Unit,
    color: Color? = null,
    label: String = "+1",
) {
    val tick = rememberTick()
    val accent = color ?: AppTheme.palette.accent
    val onAccent = if (AppTheme.palette.background.red + AppTheme.palette.background.green < 0.6f) Color(0xFF111111) else Color.White
    Box(
        Modifier
            .bounceOn(bounceKey)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) accent else AppTheme.palette.surfaceAlt)
            .pressable(
                onClick = { if (enabled) { tick(); onIncrement() } },
                onLongClick = { tick(); onLongPress() },
                scaleTo = 0.85f,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Mono.style(13, FontWeight.SemiBold), color = if (enabled) onAccent else AppTheme.palette.textTertiary, maxLines = 1)
    }
}

@Composable
fun NumberDialog(title: String, initial: Int, max: Int?, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value >= 0 && (max == null || value <= max)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> text = t.filter { it.isDigit() }.take(5) },
                    singleLine = true,
                    textStyle = Mono.style(20),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { if (max != null) Text("/$max", style = Mono.style(16), color = AppTheme.palette.textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(value!!) }) { Text(t("Salvar")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancelar")) } },
        shape = RoundedCornerShape(6.dp),
    )
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(start = if (onBack == null) 20.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar")) }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    collapsed: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = Mono.style(13), color = AppTheme.palette.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = AppTheme.palette.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onAction).padding(8.dp))
        }
        if (collapsed != null) {
            Icon(
                Icons.Default.KeyboardArrowDown, null,
                tint = AppTheme.palette.textTertiary,
                modifier = Modifier.size(20.dp).rotate(androidx.compose.animation.core.animateFloatAsState(if (collapsed) -90f else 0f, Springs.smooth, label = "arrow").value),
            )
        }
    }
}

@Composable
fun LinkRow(
    text: String,
    detail: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconColor: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = if (icon != null) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val c = iconColor ?: AppTheme.palette.accent
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = Mono.style(13), color = AppTheme.palette.textSecondary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppTheme.palette.textTertiary, modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
}

/** Opção selecionável discreta (sem "pílula" arredondada). */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val p = AppTheme.palette
    val border by androidx.compose.animation.animateColorAsState(if (selected) p.accent else p.divider, label = "pill")
    val bgc by androidx.compose.animation.animateColorAsState(if (selected) p.accent.copy(alpha = 0.16f) else Color.Transparent, label = "pillbg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) p.accent else p.textSecondary, label = "pillfg")
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .background(bgc)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/**
 * Seletor segmentado (ex.: Anime | Mangá). A pílula de fundo desliza com mola;
 * [position] permite seguir o arraste de um pager (página + fração).
 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, position: Float? = null) {
    val p = AppTheme.palette
    val widths = remember(options.size) { androidx.compose.runtime.mutableStateListOf<Int>().apply { repeat(options.size) { add(0) } } }
    val offsets = remember(options.size) { androidx.compose.runtime.mutableStateListOf<Int>().apply { repeat(options.size) { add(0) } } }
    val target = position ?: selected.toFloat()
    val animated by androidx.compose.animation.core.animateFloatAsState(target, if (position != null) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(0.75f, 400f), label = "seg")
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(p.surfaceAlt).padding(3.dp)) {
        // Pílula interpolada entre as opções.
        val i0 = animated.toInt().coerceIn(0, options.lastIndex)
        val i1 = (i0 + 1).coerceAtMost(options.lastIndex)
        val f = animated - i0
        val x = offsets[i0] + (offsets[i1] - offsets[i0]) * f
        val w = widths[i0] + (widths[i1] - widths[i0]) * f
        if (w > 0) Box(
            Modifier.graphicsLayer { translationX = x }
                .width(with(density) { w.toDp() }).height(32.dp)
                .clip(RoundedCornerShape(16.dp)).background(p.accent),
        )
        Row {
            options.forEachIndexed { i, o ->
                val near = 1f - kotlin.math.abs(animated - i).coerceIn(0f, 1f)
                val onAccent = if (p.background.red + p.background.green < 0.6f) Color(0xFF111111) else Color.White
                val c = androidx.compose.ui.graphics.lerp(p.textSecondary, onAccent, near)
                Box(
                    Modifier.height(32.dp)
                        .onGloballyPositioned { lc -> widths[i] = lc.size.width; offsets[i] = lc.positionInParent().x.toInt() }
                        .clip(RoundedCornerShape(16.dp)).clickable { onSelect(i) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(o, style = MaterialTheme.typography.labelLarge, color = c) }
            }
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.palette.textSecondary)
    }
}

@Composable
fun MessageBar(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = AppTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp))
            .background(p.accent.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = p.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = p.accent) }
        }
    }
}

fun countdown(seconds: Long): String = when {
    seconds <= 0 -> t("agora")
    seconds < 3600 -> t("{0} min", (seconds / 60).coerceAtLeast(1))
    seconds < 48 * 3600 -> "${seconds / 3600} h"
    else -> "${seconds / 86400} d"
}

fun compactNumber(n: Int): String = when {
    n >= 1_000_000 -> t("%.1f mi").format(n / 1_000_000.0).replace('.', ',')
    n >= 1_000 -> t("%.1f mil").format(n / 1_000.0).replace('.', ',').replace(",0 ", " ")
    else -> "$n"
}


/**
 * Alterna entre grade e lista. O ícone é desenhado aqui: quatro quadrados viram quatro
 * linhas (e o contrário) com mola, girando de leve no caminho. Mostra o modo para onde vai.
 */
@Composable
fun LayoutToggle(grid: Boolean, onToggle: () -> Unit) {
    val p = AppTheme.palette
    // t = 1 desenha a grade (quando está em lista), t = 0 desenha a lista.
    val t by androidx.compose.animation.core.animateFloatAsState(
        if (grid) 0f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 380f), label = "layout",
    )
    IconButton(onClick = onToggle) {
        androidx.compose.foundation.Canvas(
            Modifier.size(22.dp).graphicsLayer { rotationZ = kotlin.math.sin(t * Math.PI.toFloat()) * 45f }
                .semantics { contentDescription = if (grid) t("Ver em lista") else t("Ver em grade") },
        ) {
            val u = size.minDimension / 24f
            // Retângulos (x, y, w, h) da grade e da lista.
            val gridR = listOf(floatArrayOf(2f, 2f, 9f, 9f), floatArrayOf(13f, 2f, 9f, 9f), floatArrayOf(2f, 13f, 9f, 9f), floatArrayOf(13f, 13f, 9f, 9f))
            val listR = listOf(floatArrayOf(2f, 3f, 20f, 3f), floatArrayOf(2f, 8.5f, 20f, 3f), floatArrayOf(2f, 14f, 20f, 3f), floatArrayOf(2f, 19.5f, 14f, 3f))
            for (i in 0 until 4) {
                val g = gridR[i]; val l = listR[i]
                fun lerp(a: Float, b: Float) = a + (b - a) * t
                val x = lerp(l[0], g[0]); val y = lerp(l[1], g[1]); val w = lerp(l[2], g[2]); val h = lerp(l[3], g[3])
                drawRoundRect(
                    if (i == 3) p.accent else p.text,
                    topLeft = androidx.compose.ui.geometry.Offset(x * u, y * u),
                    size = androidx.compose.ui.geometry.Size(w * u, h * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(lerp(1.5f, 2.5f) * u),
                )
            }
        }
    }
}

/** Cabeçalho de seção de status, com o ícone do status. */
@Composable
fun StatusHeader(status: dev.viko.sessao.domain.WatchStatus, kind: dev.viko.sessao.domain.MediaType, count: Int, collapsed: Boolean? = null, onToggle: (() -> Unit)? = null) {
    val p = AppTheme.palette
    Row(
        Modifier.fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sc = dev.viko.sessao.ui.theme.statusColor(status)
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(sc.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(status.icon(), null, tint = sc, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(status.label(kind), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(p.surfaceAlt).padding(horizontal = 7.dp, vertical = 1.dp)) {
            Text("$count", style = Mono.style(12), color = p.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        if (collapsed != null) Icon(
            Icons.Default.KeyboardArrowDown, null, tint = p.textTertiary,
            modifier = Modifier.size(20.dp).rotate(androidx.compose.animation.core.animateFloatAsState(if (collapsed) -90f else 0f, Springs.smooth, label = "arrow").value),
        )
    }
}

/**
 * Segmented ligado a um pager. Lê a posição do arraste aqui dentro, para que só este
 * componente recomponha durante o gesto (e não a tela inteira).
 */
@Composable
fun PagerSegmented(pager: androidx.compose.foundation.pager.PagerState, options: List<String>, modifier: Modifier = Modifier) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Segmented(
        options, pager.currentPage, { scope.launch { pager.animateScrollToPage(it) } }, modifier,
        position = pager.currentPage + pager.currentPageOffsetFraction,
    )
}
