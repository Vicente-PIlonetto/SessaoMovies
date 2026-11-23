package dev.viko.sessao.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.api.TmdbImages
import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.Item
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.Dates
import dev.viko.sessao.ui.components.EmptyState
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.MessageBar
import dev.viko.sessao.ui.components.PagerSegmented
import dev.viko.sessao.ui.components.Pill
import dev.viko.sessao.ui.components.SectionHeader
import dev.viko.sessao.ui.components.SkeletonRows
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.enterAnimation
import dev.viko.sessao.ui.components.pressable
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono
import dev.viko.sessao.ui.theme.rememberCoverTint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** Um episódio na Agenda. */
data class AiringUi(
    val key: TitleKey,
    val name: String,
    val posterPath: String?,
    val backdropPath: String?,
    val color: Int?,
    val ep: Ep,
    val date: LocalDate,
    val mine: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(private val c: AppContainer) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val error = MutableStateFlow<String?>(null)
    val remoteWeek = MutableStateFlow<List<AiringUi>?>(null)
    val remoteMonth = MutableStateFlow<Map<YearMonth, List<AiringUi>>>(emptyMap())
    val loading = MutableStateFlow(false)

    private val items = c.lists.items

    /** Episódios das séries da minha lista entre [from] e [to] (cache das temporadas + próximo anunciado). */
    private fun mine(from: LocalDate, to: LocalDate): Flow<List<AiringUi>> =
        combine(c.db.episodes().observeRange(from.toString(), to.toString()), items) { eps, list ->
            val shows = list.filter { it.type == MediaType.TV && it.entry.status != WatchStatus.DROPPED }.associateBy { it.entry.id }
            val fromCache = eps.mapNotNull { e ->
                val item = shows[e.showId] ?: return@mapNotNull null
                val d = Dates.parse(e.airDate) ?: return@mapNotNull null
                ui(item, e.ep, d)
            }
            val announced = shows.values.mapNotNull { item ->
                val ne = item.title?.nextEpisode ?: return@mapNotNull null
                val d = Dates.parse(ne.airDate) ?: return@mapNotNull null
                if (d < from || d > to) null else ui(item, ne.ep, d)
            }
            (fromCache + announced).distinctBy { it.key to it.ep }.sortedWith(compareBy<AiringUi> { it.date }.thenBy { it.name })
        }

    private fun ui(item: Item, ep: Ep, d: LocalDate) = AiringUi(
        item.key, item.title?.name.orEmpty(), item.title?.posterPath, item.title?.backdropPath, item.title?.posterColor, ep, d, mine = true,
    )

    val week = mine(LocalDate.now(), LocalDate.now().plusDays(6)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val monthMine = month.flatMapLatest { m -> mine(m.atDay(1), m.atEndOfMonth()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setShowAll(v: Boolean) = viewModelScope.launch { c.settings.setScheduleShowAll(v) }
    fun shiftMonth(by: Long) { month.value = month.value.plusMonths(by) }

    /** "Tudo em exibição": séries populares com episódio no período, fora as da minha lista. */
    fun loadRemoteWeek(force: Boolean = false) = viewModelScope.launch {
        if (remoteWeek.value != null && !force) return@launch
        loading.value = true
        try {
            remoteWeek.value = remote(LocalDate.now(), LocalDate.now().plusDays(6))
            error.value = null
        } catch (e: Exception) { error.value = e.toUserMessage() } finally { loading.value = false }
    }

    fun loadRemoteMonth(m: YearMonth) = viewModelScope.launch {
        if (remoteMonth.value.containsKey(m)) return@launch
        loading.value = true
        try {
            remoteMonth.value = remoteMonth.value + (m to remote(m.atDay(1), m.atEndOfMonth()))
            error.value = null
        } catch (e: Exception) { error.value = e.toUserMessage() } finally { loading.value = false }
    }

    private suspend fun remote(from: LocalDate, to: LocalDate): List<AiringUi> {
        val myIds = c.db.entries().all().filter { it.type == MediaType.TV }.map { it.id }.toSet()
        return c.titles.airingEpisodes(from, to, myIds).mapNotNull { (s, e) ->
            val d = Dates.parse(e.airDate) ?: return@mapNotNull null
            AiringUi(s.key, s.name, s.posterPath, s.backdropPath, null, e.ep, d, mine = false)
        }
    }
}

@Composable
fun ScheduleScreen(nav: Nav) {
    val vm = appViewModel { ScheduleViewModel(it) }
    val s = LocalSettings.current
    val error by vm.error.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 2 }
    LaunchedEffect(s.scheduleShowAll) { if (s.scheduleShowAll) vm.loadRemoteWeek() }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Agenda"))
        PagerSegmented(pager, listOf(t("Semana"), t("Calendário")), Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(t("Minha lista"), !s.scheduleShowAll, { vm.setShowAll(false) })
            Pill(t("Tudo em exibição"), s.scheduleShowAll, { vm.setShowAll(true) })
        }
        if (s.scheduleShowAll) error?.let { MessageBar(it) }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page == 0) WeekList(vm, nav, s.scheduleShowAll) else MonthCalendar(vm, nav, s.scheduleShowAll)
        }
    }
}

@Composable
private fun WeekList(vm: ScheduleViewModel, nav: Nav, showAll: Boolean) {
    val mine by vm.week.collectAsStateWithLifecycle()
    val remote by vm.remoteWeek.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val list = mine ?: return SkeletonRows()
    val all = (list + if (showAll) remote.orEmpty() else emptyList()).sortedWith(compareBy<AiringUi> { it.date }.thenByDescending { it.mine }.thenBy { it.name })
    val days = all.groupBy { it.date }
    LazyColumn(Modifier.fillMaxSize()) {
        if (days.isEmpty()) item {
            EmptyState(if (showAll && loading) t("Carregando...") else t("Nenhum episódio nos próximos 7 dias."))
        }
        var index = 0
        days.forEach { (day, rows) ->
            val i0 = index++
            item(key = "d-$day") { Box(Modifier.enterAnimation(i0)) { DayBanner(day, today, rows) } }
            rows.forEach { row ->
                val i = index++
                item(key = "w-${row.key}-${row.ep}") { Box(Modifier.enterAnimation(i)) { AiringCard(row, showAll) { nav.detail(row.key) } } }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** Iniciais dos dias (domingo primeiro). */
private val WEEKDAYS: List<String> get() = (0..6).map { i ->
    DayOfWeek.SUNDAY.plus(i.toLong()).getDisplayName(TextStyle.NARROW, java.util.Locale.forLanguageTag("pt-BR"))
}

@Composable
private fun MonthCalendar(vm: ScheduleViewModel, nav: Nav, showAll: Boolean) {
    val p = AppTheme.palette
    val month by vm.month.collectAsStateWithLifecycle()
    val mine by vm.monthMine.collectAsStateWithLifecycle()
    val remoteAll by vm.remoteMonth.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var selected by remember { mutableStateOf(today) }
    LaunchedEffect(month) { if (YearMonth.from(selected) != month) selected = if (YearMonth.from(today) == month) today else month.atDay(1) }
    LaunchedEffect(month, showAll) { if (showAll) vm.loadRemoteMonth(month) }
    val all = mine.orEmpty() + if (showAll) remoteAll[month].orEmpty() else emptyList()
    val byDay = all.groupBy { it.date }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Mês anterior")) }
                AnimatedContent(month, Modifier.weight(1f), label = "mes", transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
                }) { ym ->
                    Text(Dates.month(ym.atDay(1)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                IconButton(onClick = { vm.shiftMonth(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Próximo mês")) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                WEEKDAYS.forEach { d -> Text(d, style = MaterialTheme.typography.labelMedium, color = p.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value % 7
            val rows = (offset + month.lengthOfMonth() + 6) / 7
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (cIdx in 0 until 7) {
                            val n = r * 7 + cIdx - offset + 1
                            if (n < 1 || n > month.lengthOfMonth()) { Spacer(Modifier.weight(1f)); continue }
                            val date = month.atDay(n)
                            val list = byDay[date].orEmpty().sortedByDescending { it.mine }
                            DayCell(date, list, date == today, date == selected, r * 7 + cIdx, Modifier.weight(1f)) { selected = date }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val dayRows = byDay[selected].orEmpty().sortedWith(compareByDescending<AiringUi> { it.mine }.thenBy { it.name })
        item(key = "sel-$selected") { SectionHeader(dayLabel(selected, today), dayRows.size) }
        if (dayRows.isEmpty()) item(key = "empty-$selected") {
            Text(
                if (loading && showAll) t("Carregando...") else t("Nenhum episódio neste dia."),
                style = MaterialTheme.typography.bodySmall, color = p.textTertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        dayRows.forEachIndexed { i, row ->
            item(key = "c-${row.key}-${row.ep}") { Box(Modifier.enterAnimation(i)) { AiringCard(row, showAll) { nav.detail(row.key) } } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** Um dia do calendário: o pôster do primeiro episódio dá a cor; o número fica por cima. */
@Composable
private fun DayCell(date: LocalDate, items: List<AiringUi>, isToday: Boolean, isSelected: Boolean, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val p = AppTheme.palette
    val first = items.firstOrNull()
    val shape = RoundedCornerShape(12.dp)
    val sel by androidx.compose.animation.core.animateFloatAsState(
        if (isSelected) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "sel",
    )
    Box(
        modifier.aspectRatio(0.78f).enterAnimation(index / 7, distance = 12.dp)
            .graphicsLayer { val k = 1f + 0.06f * sel; scaleX = k; scaleY = k }
            .clip(shape)
            .background(
                when {
                    isSelected && first == null -> p.accent.copy(alpha = 0.35f)
                    isToday -> p.accent.copy(alpha = 0.18f)
                    else -> p.surfaceAlt
                },
            )
            .pressable(onClick = onClick),
    ) {
        if (first != null) {
            AsyncImage(TmdbImages.poster(first.posterPath, "w185"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f + 0.5f * sel })
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.6f to Color.Transparent)))
        }
        Text(
            "${date.dayOfMonth}",
            style = Mono.style(13, if (isToday) FontWeight.Bold else FontWeight.Medium),
            color = if (first != null) Color.White else if (isToday) p.accent else p.textSecondary,
            modifier = Modifier.padding(5.dp),
        )
        if (items.size > 1) Text(
            "${items.size}", style = Mono.style(10, FontWeight.SemiBold), color = p.background,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(6.dp))
                .background(if (items.any { it.mine }) p.accent else p.text.copy(alpha = 0.8f)).padding(horizontal = 4.dp),
        )
    }
}

fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> t("Hoje")
    today.plusDays(1) -> t("Amanhã")
    today.minusDays(1) -> t("Ontem")
    else -> Dates.weekday(day) + ", " + Dates.full(day).substringBeforeLast(' ')
}

/** Cabeçalho do dia: o fundo de uma série do dia, a data e "Hoje", "Amanhã" ou o dia da semana. */
@Composable
private fun DayBanner(day: LocalDate, today: LocalDate, rows: List<AiringUi>) {
    val p = AppTheme.palette
    val pick = rows.firstOrNull { it.backdropPath != null } ?: rows.firstOrNull()
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp).fillMaxWidth().height(96.dp).clip(RoundedCornerShape(18.dp)).background(p.surfaceAlt)) {
        AsyncImage(TmdbImages.backdrop(pick?.backdropPath) ?: TmdbImages.poster(pick?.posterPath), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.75f), 0.7f to Color.Black.copy(alpha = 0.15f))))
        Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
            Text(Dates.full(day).substringBeforeLast(' '), style = Mono.style(13), color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.width(10.dp))
            Text(dayLabel(day, today).substringBefore(','), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        Text(
            "${rows.size}", style = Mono.style(13, FontWeight.SemiBold), color = p.background,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(p.accent).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Episódio na Agenda: cartão com o pôster, o nome e "T2 · E6 hoje". */
@Composable
private fun AiringCard(row: AiringUi, markMine: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    val tint = rememberCoverTint(row.color)
    val released = !row.date.isAfter(LocalDate.now())
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(72.dp).clip(RoundedCornerShape(14.dp))
            .background(p.surfaceAlt).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(TmdbImages.poster(row.posterPath, "w185"), null, contentScale = ContentScale.Crop, modifier = Modifier.width(52.dp).fillMaxSize().background(tint.copy(alpha = 0.3f)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (released) t("{0} disponível", row.ep.label) else "${row.ep.label} ${Dates.relative(row.date)}",
                style = Mono.style(12), color = if (released && row.mine) tint else p.textSecondary,
            )
        }
        if (row.mine && markMine) Box(Modifier.padding(end = 12.dp).size(8.dp).clip(CircleShape).background(p.accent))
    }
}
