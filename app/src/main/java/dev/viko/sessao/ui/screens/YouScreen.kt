package dev.viko.sessao.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.api.TmdbImages
import dev.viko.sessao.domain.Item
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.domain.score.ScoreBuckets
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.Cover
import dev.viko.sessao.ui.components.Dates
import dev.viko.sessao.ui.components.EmptyState
import dev.viko.sessao.ui.components.FaceIcon
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.PagerSegmented
import dev.viko.sessao.ui.components.ScoreSize
import dev.viko.sessao.ui.components.ScoreView
import dev.viko.sessao.ui.components.SectionHeader
import dev.viko.sessao.ui.components.SkeletonRows
import dev.viko.sessao.ui.components.StatusIcon
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.coverKey
import dev.viko.sessao.ui.components.faceColor
import dev.viko.sessao.ui.components.pressable
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono
import dev.viko.sessao.ui.theme.statusColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

/** Números calculados da lista local. */
data class Stats(
    val series: Int,
    val movies: Int,
    val episodes: Int,
    val minutes: Long,
    val scores: List<Int>,
    val statusTv: List<Pair<WatchStatus, Int>>,
    val statusMovie: List<Pair<WatchStatus, Int>>,
    val genres: List<Pair<String, Int>>,
)

/** Episódio sem duração no TMDB conta como 45 minutos. */
private const val DEFAULT_EPISODE_MIN = 45

fun computeStats(items: List<Item>, allWatched: Int): Stats {
    val tv = items.filter { it.type == MediaType.TV }
    val mv = items.filter { it.type == MediaType.MOVIE }
    val episodeMinutes = tv.sumOf { i -> (i.watched.size).toLong() * (i.title?.runtime ?: DEFAULT_EPISODE_MIN) }
    val movieMinutes = mv.filter { it.entry.status == WatchStatus.COMPLETED }.sumOf { (it.title?.runtime ?: 0).toLong() * it.entry.timesWatched.coerceAtLeast(1) }
    val started = items.filter { it.entry.status != WatchStatus.PLANNING }
    return Stats(
        series = tv.size,
        movies = mv.size,
        episodes = allWatched,
        minutes = episodeMinutes + movieMinutes,
        scores = items.map { it.entry.scoreRaw },
        statusTv = WatchStatus.forType(MediaType.TV).map { st -> st to tv.count { it.entry.status == st } }.filter { it.second > 0 },
        statusMovie = WatchStatus.forType(MediaType.MOVIE).map { st -> st to mv.count { it.entry.status == st } }.filter { it.second > 0 },
        genres = started.flatMap { it.title?.genres.orEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
    )
}

class YouViewModel(c: AppContainer) : ViewModel() {
    val items = c.lists.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val stats = combine(c.lists.items, c.db.watched().observeAll()) { items, w -> computeStats(items, w.count { it.season >= 1 }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun YouScreen(nav: Nav) {
    val vm = appViewModel { YouViewModel(it) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 2 }
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Você")) { IconButton(onClick = { nav.settings() }) { Icon(Icons.Outlined.Settings, t("Configurações")) } }
        PagerSegmented(pager, listOf(t("Números"), t("Histórico")), Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page == 0) StatsPage(stats) else HistoryPage(items, nav)
        }
    }
}

// ---------------- Números ----------------

@Composable
private fun StatsPage(st: Stats?) {
    if (st == null) { SkeletonRows(); return }
    if (st.series + st.movies == 0) { EmptyState(t("Nada na lista ainda.")); return }
    val s = LocalSettings.current
    val pt = java.util.Locale.forLanguageTag("pt-BR")
    val md = ScoreBuckets.meanAndDeviation(st.scores)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(t("Séries"), "${st.series}", Modifier.weight(1f))
                Tile(t("Filmes"), "${st.movies}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(t("Episódios vistos"), "${st.episodes}", Modifier.weight(1f))
                Tile(t("Tempo assistido"), timeText(st.minutes, pt), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TileBox(t("Nota média"), Modifier.weight(1f)) {
                    if (md == null) Text("–", style = Mono.style(22)) else ScoreView(md.first.toInt(), s.primaryScore, s.secondaryScore, ScoreSize.Normal)
                }
                Tile(t("Desvio padrão"), md?.let { "%.1f".format(pt, it.second) } ?: "–", Modifier.weight(1f))
            }
        }
        ScoreChart(st.scores)
        StatusBars(t("Séries por status"), MediaType.TV, st.statusTv)
        StatusBars(t("Filmes por status"), MediaType.MOVIE, st.statusMovie)
        if (st.genres.isNotEmpty()) HBars(t("Gêneros mais vistos"), st.genres)
    }
}

private fun timeText(minutes: Long, pt: java.util.Locale): String = when {
    minutes <= 0 -> "0 h"
    minutes < 60 * 48 -> t("{0} h", minutes / 60)
    else -> t("{0} dias", "%.1f".format(pt, minutes / 1440.0))
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) = TileBox(label, modifier) {
    Text(value, style = Mono.style(22, FontWeight.SemiBold), color = AppTheme.palette.text)
}

@Composable
private fun TileBox(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    val p = AppTheme.palette
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt).padding(horizontal = 16.dp, vertical = 12.dp)) {
        value()
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Título do bloco + linha com o detalhe do que foi tocado. */
@Composable
private fun ChartCard(title: String, detail: String?, content: @Composable () -> Unit) {
    val p = AppTheme.palette
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surfaceAlt).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail ?: t("Toque numa barra para ver o número."), style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        content()
    }
}

private fun percent(n: Int, total: Int): String = if (total <= 0) "0%" else "%.0f%%".format(n * 100.0 / total)

/** Distribuição das notas no sistema escolhido: colunas numa cor só; carinhas no eixo quando for o caso. */
@Composable
private fun ScoreChart(raws: List<Int>) {
    val p = AppTheme.palette
    val system = LocalSettings.current.primaryScore
    val dist = remember(raws, system) { ScoreBuckets.distribution(raws, system) }
    val total = dist.sumOf { it.second }
    if (total == 0) return
    var sel by remember(system) { mutableStateOf<Int?>(null) }
    val detail = sel?.let { i -> dist.getOrNull(i)?.let { (b, n) -> t("{0}: {1} títulos ({2})", b.label, n, percent(n, total)) } }
    ChartCard(t("Notas"), detail) {
        val max = (dist.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            dist.forEachIndexed { i, (_, n) ->
                val frac by animateFloatAsState(n / max.toFloat(), label = "bar")
                val on = sel == null || sel == i
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).pressable(onClick = { sel = if (sel == i) null else i }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
                ) {
                    Text("$n", style = Mono.style(11), color = if (on) p.text else p.textTertiary, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth(0.62f).fillMaxHeight(if (n > 0) (frac * 0.82f).coerceAtLeast(0.02f) else 0f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(p.accent.copy(alpha = if (on) 1f else 0.35f)),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.divider))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dist.forEach { (b, _) ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    if (b.faceLevel != null) FaceIcon(b.faceLevel, faceColor(b.faceLevel), 20.dp, label = b.label)
                    else Text(b.label, style = Mono.style(11), color = p.textSecondary, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun StatusBars(title: String, type: MediaType, rows: List<Pair<WatchStatus, Int>>) {
    val p = AppTheme.palette
    if (rows.isEmpty()) return
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    val total = rows.sumOf { it.second }
    ChartCard(title, t("{0} no total", total)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (st, n) ->
                val col = statusColor(st)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(st, col, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(st.label(type), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BarTrack(n / max.toFloat(), col, Modifier.weight(1f))
                    Text("$n", style = Mono.style(12), color = p.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

@Composable
private fun HBars(title: String, rows: List<Pair<String, Int>>) {
    val p = AppTheme.palette
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    val total = rows.sumOf { it.second }
    var sel by remember(rows) { mutableStateOf<Int?>(null) }
    val detail = sel?.let { i -> rows.getOrNull(i)?.let { (l, n) -> t("{0}: {1} títulos ({2})", l, n, percent(n, total)) } }
    ChartCard(title, detail) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { i, (label, n) ->
                val on = sel == null || sel == i
                Row(Modifier.clip(RoundedCornerShape(6.dp)).pressable(onClick = { sel = if (sel == i) null else i }), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = if (on) p.text else p.textTertiary, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BarTrack(n / max.toFloat(), p.accent.copy(alpha = if (on) 1f else 0.35f), Modifier.weight(1f))
                    Text("$n", style = Mono.style(12), color = p.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

@Composable
private fun BarTrack(frac: Float, color: Color, modifier: Modifier) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), label = "hbar")
    Box(modifier.height(10.dp)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.02f)).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

// ---------------- Histórico ----------------

/** O que terminei, agrupado por mês (o mais recente primeiro). */
@Composable
private fun HistoryPage(items: List<Item>?, nav: Nav) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val list = items ?: return SkeletonRows()
    val done = list.filter { it.entry.status == WatchStatus.COMPLETED && it.entry.finishedAt != null }.sortedByDescending { it.entry.finishedAt }
    if (done.isEmpty()) { EmptyState(t("Nada terminado ainda.")); return }
    val byMonth = done.groupBy { YearMonth.from(Dates.of(it.entry.finishedAt!!)) }
    LazyColumn(Modifier.fillMaxSize()) {
        byMonth.forEach { (month, rows) ->
            item(key = "m-$month") { SectionHeader(Dates.month(month.atDay(1)), rows.size) }
            items(rows, key = { "h-${it.key}" }) { i ->
                val k = coverKey("hist", i.key)
                Row(
                    Modifier.fillMaxWidth().pressable(onClick = { nav.detail(i.key, k) }, scaleTo = 0.98f).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(TmdbImages.poster(i.title?.posterPath, "w185"), 40.dp, color = i.title?.posterColor, sharedKey = k)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(i.title?.name ?: "…", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(i.type.label, Dates.full(Dates.of(i.entry.finishedAt!!)), i.entry.timesWatched.takeIf { it > 1 }?.let { t("{0}ª vez", it) }).joinToString(" · "),
                            style = Mono.style(12), color = p.textSecondary,
                        )
                    }
                    if (i.entry.scoreRaw > 0) ScoreView(i.entry.scoreRaw, s.primaryScore, null, ScoreSize.Small)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
