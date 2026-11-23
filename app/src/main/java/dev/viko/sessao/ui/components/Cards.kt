package dev.viko.sessao.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.viko.sessao.data.api.TitleSummary
import dev.viko.sessao.data.api.TmdbImages
import dev.viko.sessao.data.api.toTitle
import dev.viko.sessao.data.settings.Settings
import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.Item
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.Progress
import dev.viko.sessao.domain.SeasonInfo
import dev.viko.sessao.domain.Title
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono
import dev.viko.sessao.ui.theme.rememberCoverTint
import dev.viko.sessao.ui.theme.statusColor

val LocalSettings = staticCompositionLocalOf { Settings() }

/** Status dos itens da minha lista, para marcar cards de busca e Descobrir. */
val LocalStatusMap = staticCompositionLocalOf<Map<TitleKey, WatchStatus>> { emptyMap() }

/** Abre o menu rápido de status e nota (toque longo em qualquer pôster). */
val LocalQuickStatus = staticCompositionLocalOf<(Title) -> Unit> { {} }

fun nowEpoch() = System.currentTimeMillis() / 1000

/** Texto que "rola" para cima quando muda (progresso, contagens). */
@Composable
fun AnimatedProgressText(text: String, style: TextStyle, color: Color) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(220)) { it } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(180)) { -it } + fadeOut(tween(180)))
        },
        label = "progress",
    ) { Text(it, style = style, color = color, maxLines = 1) }
}

/** "2h 46min" ou "48min". */
fun runtimeText(minutes: Int?): String? {
    val m = minutes?.takeIf { it > 0 } ?: return null
    return if (m >= 60) t("{0}h {1}min", m / 60, m % 60).replace(" 0min", "") else t("{0}min", m)
}

/** Linha de mono embaixo do pôster: progresso da série ou ano e duração do filme. */
fun Item.metaText(): String = if (type == MediaType.TV) progressText
else listOfNotNull(title?.year?.toString(), runtimeText(title?.runtime)).joinToString(" · ").ifEmpty { "–" }

/** Célula da grade do Início: pôster com a barra de progresso, título, progresso, nota e "+1". */
@Composable
fun ItemGridCell(
    item: Item,
    sharedKey: String?,
    onClick: () -> Unit,
    onIncrement: () -> Unit,
    onPickEpisode: () -> Unit,
    onMovieWatched: () -> Unit,
    onScore: () -> Unit,
) {
    val quick = LocalQuickStatus.current
    val title = item.title
    val tint = rememberCoverTint(title?.posterColor)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        Column(Modifier.pressable(onClick = onClick, onLongClick = { title?.let(quick) })) {
            Box(Modifier.progressGlow(item.watchedMain, tint)) {
                Cover(TmdbImages.poster(title?.posterPath), w, color = title?.posterColor, sharedKey = sharedKey)
                if (item.hasNew) Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp))
                        .background(tint).padding(horizontal = 6.dp, vertical = 2.dp),
                ) { Text("+${item.remaining}", style = Mono.style(11, FontWeight.SemiBold), color = Color.Black) }
                if (item.type == MediaType.TV && item.total > 0) {
                    ProgressBar(item.watchedMain / item.total.toFloat(), tint, Modifier.align(Alignment.BottomCenter).padding(6.dp), height = 3.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(title?.name ?: "…", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(32.dp))
            AnimatedProgressText(item.metaText(), Mono.style(12, FontWeight.Medium), tint)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScoreChip(item.entry.scoreRaw, onScore) }
                when {
                    item.type == MediaType.TV && item.entry.status != WatchStatus.COMPLETED && item.entry.status != WatchStatus.PLANNING ->
                        PlusOneButton(item.nextAired, item.watchedMain, onIncrement, onPickEpisode, tint)
                    item.type == MediaType.MOVIE && item.entry.status == WatchStatus.PLANNING ->
                        PlusOneButton(true, item.entry.status, onMovieWatched, onMovieWatched, tint, label = t("Visto"))
                }
            }
        }
    }
}

/** Nota em chip; sem nota, "Dar nota". Toque abre o seletor. */
@Composable
fun ScoreChip(raw: Int, onClick: () -> Unit) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(p.surfaceAlt).pressable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp).bounceOn(raw),
    ) {
        if (raw > 0) ScoreView(raw, s.primaryScore, null, ScoreSize.Small)
        else Text(t("Dar nota"), style = MaterialTheme.typography.labelSmall, color = p.textSecondary, maxLines = 1)
    }
}

/** Card do Descobrir e de "semelhantes": pôster grande e texto embaixo. Toque longo muda o status. */
@Composable
fun SummaryCard(item: TitleSummary, onClick: () -> Unit, width: Dp = 112.dp, sharedKey: String? = null) {
    val p = AppTheme.palette
    val status = LocalStatusMap.current[item.key]
    val quick = LocalQuickStatus.current
    Column(Modifier.width(width).pressable(onClick = onClick, onLongClick = { quick(item.toTitle()) })) {
        Cover(TmdbImages.poster(item.posterPath), width, sharedKey = sharedKey)
        Spacer(Modifier.height(6.dp))
        Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = p.text)
        val meta = listOfNotNull(item.key.type.label, item.year?.toString(), item.voteAverage?.let { "${(it * 10).toInt()}%" }).joinToString(" · ")
        Text(meta, style = Mono.style(11), color = p.textSecondary, maxLines = 1)
        if (status != null) StatusLabel(status, item.key.type)
    }
}

@Composable
fun SummaryRow(items: List<TitleSummary>, keyPrefix: String, onClick: (TitleSummary, String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = { it.key.toString() }) { m ->
            val k = coverKey(keyPrefix, m.key)
            SummaryCard(m, onClick = { onClick(m, k) }, sharedKey = k)
        }
    }
}

/** Linha de resultado de busca: pôster, título, tipo e ano, e o status se já está na lista. */
@Composable
fun SummaryListRow(item: TitleSummary, sharedKey: String?, onClick: () -> Unit) {
    val p = AppTheme.palette
    val status = LocalStatusMap.current[item.key]
    val quick = LocalQuickStatus.current
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick, onLongClick = { quick(item.toTitle()) }, scaleTo = 0.98f)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(TmdbImages.poster(item.posterPath, "w185"), 46.dp, sharedKey = sharedKey)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.key.type.label, item.year?.toString()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            item.voteAverage?.let { Text("TMDB ${(it * 10).toInt()}%", style = Mono.style(12), color = p.textTertiary) }
        }
        if (status != null) StatusLabel(status, item.key.type)
    }
}

/** Uma linha de grade com [columns] células iguais. */
@Composable
fun <T> GridRow(items: List<T>, columns: Int, cell: @Composable (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { Box(Modifier.weight(1f)) { cell(it) } }
        repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * Marcar até um episódio específico (toque longo no "+1"). Marca os anteriores junto.
 * Especiais (temporada 0) marcam só o episódio escolhido.
 */
@Composable
fun EpisodePickerDialog(
    seasons: List<SeasonInfo>,
    watched: Set<Ep>,
    initial: Ep?,
    onDismiss: () -> Unit,
    onPick: (Ep) -> Unit,
) {
    val p = AppTheme.palette
    val all = remember(seasons) { seasons.filter { it.episodeCount > 0 }.sortedBy { if (it.number == 0) Int.MAX_VALUE else it.number } }
    var season by remember { mutableStateOf(initial?.season ?: all.firstOrNull { it.number >= 1 }?.number ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Marcar até")) },
        text = {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(all, key = { it.number }) { s ->
                        Pill(if (s.number == 0) t("Especiais") else "T${s.number}", s.number == season, { season = s.number })
                    }
                }
                Spacer(Modifier.height(12.dp))
                val count = all.firstOrNull { it.number == season }?.episodeCount ?: 0
                FlowRow(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..count).forEach { n ->
                        val ep = Ep(season, n)
                        val seen = ep in watched
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (seen) p.accent.copy(alpha = 0.2f) else p.surfaceAlt)
                                .clickable { onPick(ep) },
                            contentAlignment = Alignment.Center,
                        ) { Text("$n", style = Mono.style(14, FontWeight.Medium), color = if (seen) p.accent else p.text) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Fechar")) } },
        shape = RoundedCornerShape(16.dp),
        containerColor = p.background,
    )
}

/** Mudar o status sem abrir o título. */
@Composable
fun QuickStatusSheet(
    title: Title,
    current: WatchStatus?,
    onDismiss: () -> Unit,
    onPick: (WatchStatus) -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onScore: (() -> Unit)?,
) {
    val p = AppTheme.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = p.background,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(TmdbImages.poster(title.posterPath, "w185"), 40.dp, color = title.posterColor)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(title.type.label, title.year?.toString()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            WatchStatus.forType(title.type).forEach { st ->
                val sel = st == current
                SheetOption(st.icon(), st.label(title.type), if (sel) statusColor(st) else p.text, sel, statusColor(st)) { onPick(st) }
            }
            ThinDivider(Modifier.padding(vertical = 6.dp))
            if (current != null && onScore != null) SheetOption(Icons.Outlined.StarOutline, t("Dar nota"), p.accent, false) { onScore() }
            SheetOption(Icons.AutoMirrored.Outlined.OpenInNew, t("Abrir"), p.textSecondary, false) { onOpen() }
            if (current != null) SheetOption(Icons.Outlined.Delete, t("Remover da lista"), p.textSecondary, false) { onRemove() }
        }
    }
}

@Composable
fun SheetOption(icon: ImageVector, text: String, color: Color, selected: Boolean, iconColor: Color = color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .then(if (selected) Modifier.background(iconColor.copy(alpha = 0.14f)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal), color = color)
    }
}

/** Próximo episódio a partir dos episódios marcados, para abrir o seletor já na temporada certa. */
fun Item.pickerStart(): Ep? = Progress.next(watched, seasons) ?: Progress.last(watched, seasons)
