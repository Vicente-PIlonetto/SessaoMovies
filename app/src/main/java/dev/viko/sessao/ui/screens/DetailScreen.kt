package dev.viko.sessao.ui.screens

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.api.DetailExtras
import dev.viko.sessao.data.api.TmdbImages
import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.EpisodeInfo
import dev.viko.sessao.domain.Item
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.ProviderRules
import dev.viko.sessao.domain.ScoreSource
import dev.viko.sessao.domain.SeasonInfo
import dev.viko.sessao.domain.Title
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.AnimatedProgressText
import dev.viko.sessao.ui.components.Cover
import dev.viko.sessao.ui.components.Dates
import dev.viko.sessao.ui.components.EpisodePickerDialog
import dev.viko.sessao.ui.components.LocalQuickStatus
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.MessageBar
import dev.viko.sessao.ui.components.PlusOneButton
import dev.viko.sessao.ui.components.ProgressBar
import dev.viko.sessao.ui.components.ScorePickerSheet
import dev.viko.sessao.ui.components.ScoreSize
import dev.viko.sessao.ui.components.ScoreView
import dev.viko.sessao.ui.components.SectionHeader
import dev.viko.sessao.ui.components.SkeletonDetail
import dev.viko.sessao.ui.components.StatusIcon
import dev.viko.sessao.ui.components.StreamingApps
import dev.viko.sessao.ui.components.SummaryRow
import dev.viko.sessao.ui.components.bounceOn
import dev.viko.sessao.ui.components.enterAnimation
import dev.viko.sessao.ui.components.icon
import dev.viko.sessao.ui.components.pickerStart
import dev.viko.sessao.ui.components.pressable
import dev.viko.sessao.ui.components.runtimeText
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.LocalPalette
import dev.viko.sessao.ui.theme.Mono
import dev.viko.sessao.ui.theme.rememberCoverTint
import dev.viko.sessao.ui.theme.statusColor
import dev.viko.sessao.ui.theme.tinted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val c: AppContainer, val key: TitleKey) : ViewModel() {
    val title = c.titles.observe(key).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val item = c.lists.item(key).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val episodes = c.titles.episodes(key.id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val extras = MutableStateFlow(c.titles.extras(key))
    val error = MutableStateFlow<String?>(null)
    val loadingSeasons = MutableStateFlow<Set<Int>>(emptySet())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        try {
            c.titles.refresh(key)
            extras.value = c.titles.extras(key)
            if (key.type == MediaType.TV && c.lists.get(key) != null) { c.lists.afterRefresh(key); c.lists.recompute(key) }
            error.value = null
        } catch (e: Exception) {
            // Com o título salvo, a tela abre offline; o erro só aparece se não houver nada.
            error.value = e.toUserMessage()
        }
    }

    fun loadSeason(n: Int) = viewModelScope.launch {
        if (n in loadingSeasons.value) return@launch
        loadingSeasons.value = loadingSeasons.value + n
        runCatching { c.titles.loadSeason(key.id, n) }
        loadingSeasons.value = loadingSeasons.value - n
    }

    fun add(t: Title, status: WatchStatus) = viewModelScope.launch { c.lists.add(t, status); c.lists.recompute(key) }
    fun increment() = viewModelScope.launch { c.lists.increment(key) }
    fun markUpTo(ep: Ep) = viewModelScope.launch { if (ep.season == 0) c.lists.setEpisode(key, ep, true) else c.lists.markUpTo(key, ep) }
    fun setEpisode(ep: Ep, on: Boolean) = viewModelScope.launch { c.lists.setEpisode(key, ep, on) }
    fun setSeason(n: Int, on: Boolean) = viewModelScope.launch { c.lists.setSeason(key, n, on) }
    fun setScore(raw: Int) = viewModelScope.launch { c.lists.setScore(key, raw) }
    fun rewatch() = viewModelScope.launch { c.lists.rewatch(key) }
    fun movieWatched() = viewModelScope.launch { c.lists.setStatus(key, WatchStatus.COMPLETED) }
    fun saveEdit(note: String, times: Int) = viewModelScope.launch {
        val e = c.lists.get(key) ?: return@launch
        c.lists.setNote(key, note)
        c.lists.setDates(key, e.startedAt, e.finishedAt, times)
    }
    fun remove() = viewModelScope.launch { c.lists.remove(key) }
}

@Composable
fun DetailScreen(nav: Nav, key: TitleKey, coverKey: String? = null) {
    val vm = appViewModel(key = "detail-$key") { DetailViewModel(it, key) }
    val title by vm.title.collectAsStateWithLifecycle()
    val item by vm.item.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loadingSeasons by vm.loadingSeasons.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val p = AppTheme.palette
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    var scorePicker by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }

    val t = title
    if (t == null) {
        Box(Modifier.fillMaxSize()) {
            if (error != null) Column(Modifier.statusBarsPadding().padding(top = 56.dp)) { MessageBar(error!!, t("Tentar de novo")) { vm.refresh() } }
            else SkeletonDetail()
            IconButton(onClick = nav::back, modifier = Modifier.statusBarsPadding().padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar")) }
        }
        return
    }
    val tint = rememberCoverTint(t.posterColor)
    val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(t.posterColor) { reveal.animateTo(1f, androidx.compose.animation.core.tween(700)) }
    val tintedPalette = remember(tint, p) { p.tinted(tint, 0.10f) }
    val baseBg = p.background

    CompositionLocalProvider(LocalPalette provides tintedPalette) {
        LazyColumn(
            Modifier.fillMaxSize().drawBehind {
                drawRect(baseBg)
                drawRect(tintedPalette.background.copy(alpha = reveal.value))
            },
            state = listState,
        ) {
            item { Header(t, coverKey, listState, nav::back) }
            error?.takeIf { t.updatedAt == 0L }?.let { item { MessageBar(it, t("Tentar de novo")) { vm.refresh() } } }
            item {
                ProgressCard(
                    t, item,
                    onAdd = { vm.add(t, it) },
                    onIncrement = vm::increment, onPick = { picker = true },
                    onScore = { scorePicker = true }, onEdit = { editor = true }, onRewatch = vm::rewatch,
                    onMovieWatched = vm::movieWatched,
                    onShare = { share(ctx, t) },
                    onLetterboxd = { StreamingApps.openLetterboxd(ctx, t.id) },
                )
                ScoreLine(item, t)
                t.nextEpisode?.let { ne ->
                    if (t.type == MediaType.TV) Text(Dates.nextEpisode(ne), style = Mono.style(13), color = AppTheme.palette.textSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            item { InfoTable(t) }
            if (t.genres.isNotEmpty()) item {
                FlowRow(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).enterAnimation(4),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { t.genres.forEach { g -> GenreChip(g) } }
            }
            item { WhereToWatch(t, s.myServiceIds) }
            if (t.overview.isNotBlank()) item { Synopsis(t.overview) }
            if (t.type == MediaType.TV && t.seasons.any { it.episodeCount > 0 }) {
                item { SectionHeader(t("Temporadas"), t.seasons.count { it.number >= 1 }) }
                items(t.seasons.filter { it.episodeCount > 0 }.sortedBy { if (it.number == 0) Int.MAX_VALUE else it.number }, key = { "s${it.number}" }) { season ->
                    SeasonBlock(
                        season, episodes.filter { it.season == season.number }, item?.watched.orEmpty(),
                        loading = season.number in loadingSeasons,
                        lastAired = t.lastAired?.ep, ended = t.ended,
                        onExpand = { if (episodes.none { it.season == season.number }) vm.loadSeason(season.number) },
                        onEpisode = vm::setEpisode, onSeason = vm::setSeason,
                    )
                }
            }
            extras?.let { x -> extrasSection(x.copy(similar = x.similar.distinctBy { it.key }, cast = x.cast.distinctBy { it.id }), t, nav) }
            item {
                Text(
                    t("Dados do TMDB. Onde assistir: JustWatch."),
                    style = MaterialTheme.typography.labelSmall, color = AppTheme.palette.textTertiary,
                    modifier = Modifier.padding(20.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val it0 = item
    if (scorePicker && it0 != null) ScorePickerSheet(
        initialRaw = it0.entry.scoreRaw, system = s.primaryScore, subtitle = t.name,
        onDismiss = { scorePicker = false },
        onSave = { vm.setScore(it.raw); scorePicker = false },
    )
    if (editor && it0 != null) EditDialog(it0, onDismiss = { editor = false }, onSave = { note, times -> vm.saveEdit(note, times); editor = false }, onRemove = { vm.remove(); editor = false })
    if (picker && it0 != null) EpisodePickerDialog(it0.seasons, it0.watched, it0.pickerStart(), onDismiss = { picker = false }) { vm.markUpTo(it); picker = false }
}

private fun androidx.compose.foundation.lazy.LazyListScope.extrasSection(x: DetailExtras, t: Title, nav: Nav) {
    if (x.cast.isNotEmpty()) item {
        SectionHeader(t("Elenco"))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(x.cast, key = { it.id }) { m ->
                Column(Modifier.width(76.dp)) {
                    AsyncImage(
                        TmdbImages.profile(m.profilePath), m.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(76.dp, 100.dp).clip(RoundedCornerShape(8.dp)).background(AppTheme.palette.surfaceAlt),
                    )
                    Text(m.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    m.character?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = AppTheme.palette.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
    if (x.similar.isNotEmpty()) item {
        SectionHeader(t("Semelhantes"))
        SummaryRow(x.similar, "sim${t.key}") { m, k -> nav.detail(m.key, k) }
    }
}

private fun share(ctx: android.content.Context, t: Title) {
    val text = listOfNotNull(t.name + (t.year?.let { " ($it)" } ?: ""), "https://www.themoviedb.org/${t.type.path}/${t.id}").joinToString("\n")
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, t.name).putExtra(Intent.EXTRA_TEXT, text)
    runCatching { ctx.startActivity(Intent.createChooser(i, t("Compartilhar")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun Header(t: Title, coverKey: String?, listState: androidx.compose.foundation.lazy.LazyListState, onBack: () -> Unit) {
    val p = AppTheme.palette
    Box(Modifier.fillMaxWidth().height(330.dp).clipToBounds()) {
        // Fundo: o backdrop desfocado, com zoom e parallax ao rolar.
        AsyncImage(
            TmdbImages.backdrop(t.backdropPath) ?: TmdbImages.poster(t.posterPath), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 600f
                    translationY = off * 0.45f
                    val z = 1.08f + off / 2500f
                    scaleX = z; scaleY = z
                }
                .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(if (t.backdropPath == null) 28.dp else 6.dp) else Modifier)
                .background(p.surfaceAlt),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to p.background.copy(alpha = 0.15f), 0.45f to p.background.copy(alpha = 0.55f), 1f to p.background)))
        IconButton(
            onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = p.background.copy(alpha = 0.7f)),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar")) }
        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            Cover(TmdbImages.poster(t.posterPath), 118.dp, color = t.posterColor, sharedKey = coverKey)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).enterAnimation(1)) {
                Text(t.name, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                t.originalName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(t.type.label)
                    productionLabel(t)?.let { InfoChip(it, accent = t.type == MediaType.TV && !t.ended) }
                    t.voteAverage?.let { InfoChip("${(it * 10).toInt()}%") }
                }
            }
        }
    }
}

/** Status da produção em português. */
fun productionLabel(t: Title): String? = when (t.productionStatus) {
    "Returning Series" -> t("Em exibição")
    "Ended" -> t("Encerrada")
    "Canceled" -> if (t.type == MediaType.TV) t("Cancelada") else t("Cancelado")
    "In Production" -> t("Em produção")
    "Planned" -> if (t.type == MediaType.TV) t("Planejada") else t("Planejado")
    "Pilot" -> t("Piloto")
    "Released" -> t("Lançado")
    "Post Production" -> t("Pós-produção")
    "Rumored" -> t("Anunciado")
    null, "" -> null
    else -> t.productionStatus
}

@Composable
private fun InfoChip(text: String, accent: Boolean = false) {
    val p = AppTheme.palette
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (accent) p.accent.copy(alpha = 0.2f) else p.surfaceAlt).padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = if (accent) p.accent else p.textSecondary, maxLines = 1) }
}

@Composable
private fun GenreChip(text: String) {
    val p = AppTheme.palette
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = p.accent,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Card de progresso: status, "17/36", "+1", barra e as ações. */
@Composable
private fun ProgressCard(
    t: Title, item: Item?,
    onAdd: (WatchStatus) -> Unit,
    onIncrement: () -> Unit, onPick: () -> Unit,
    onScore: () -> Unit, onEdit: () -> Unit, onRewatch: () -> Unit, onMovieWatched: () -> Unit,
    onShare: () -> Unit, onLetterboxd: () -> Unit,
) {
    val p = AppTheme.palette
    // O chip de status abre o mesmo menu rápido do toque longo.
    val quick = LocalQuickStatus.current
    var addMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).enterAnimation(2).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(p.surfaceAlt).padding(16.dp),
    ) {
        if (item == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Fora da sua lista"), style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, modifier = Modifier.weight(1f))
                Box {
                    Button(
                        onClick = { addMenu = true }, shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.background),
                    ) { Text(t("Adicionar")) }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        WatchStatus.forType(t.type).filter { it != WatchStatus.REWATCHING }.forEach {
                            DropdownMenuItem(
                                leadingIcon = { Icon(it.icon(), null, tint = statusColor(it)) },
                                text = { Text(it.label(t.type)) }, onClick = { addMenu = false; onAdd(it) },
                            )
                        }
                    }
                }
            }
        } else {
            val st = item.entry.status
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sc = statusColor(st)
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(sc.copy(alpha = 0.18f)).clickable { quick(t) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusIcon(st, sc, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(st.label(t.type), style = MaterialTheme.typography.labelLarge, color = sc)
                }
                Spacer(Modifier.weight(1f))
                if (t.type == MediaType.TV) {
                    AnimatedProgressText(if (item.total > 0) "${item.watchedMain}/${item.total}" else item.progressText, Mono.style(16, FontWeight.SemiBold), p.accent)
                    Spacer(Modifier.width(12.dp))
                    PlusOneButton(item.nextAired, item.watchedMain, onIncrement, onPick)
                } else {
                    if (st == WatchStatus.COMPLETED) {
                        Text(if (item.entry.timesWatched > 1) t("visto {0} vezes", item.entry.timesWatched) else t("visto"), style = Mono.style(14), color = p.textSecondary)
                    } else PlusOneButton(true, st, onMovieWatched, onMovieWatched, label = t("Visto"))
                }
            }
            if (t.type == MediaType.TV) {
                if (item.total > 0) {
                    Spacer(Modifier.height(12.dp))
                    ProgressBar(item.watchedMain / item.total.toFloat(), p.accent, height = 6.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(item.progressText, item.remaining.takeIf { it > 0 }?.let { if (it == 1) t("1 episódio lançado para ver") else t("{0} episódios lançados para ver", it) }).joinToString(" · "),
                    style = Mono.style(12), color = p.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (item != null) {
                ActionIcon(Icons.Outlined.StarOutline, t("Nota"), onScore)
                ActionIcon(Icons.Outlined.Edit, t("Editar"), onEdit)
                if (item.entry.status == WatchStatus.COMPLETED) ActionIcon(Icons.Outlined.Replay, t("Rever"), onRewatch)
            }
            ActionIcon(Icons.Outlined.Share, t("Compartilhar"), onShare)
            if (t.type == MediaType.MOVIE) ActionIcon(Icons.Outlined.Sync, "Letterboxd", onLetterboxd)
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = AppTheme.palette
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).pressable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = p.text, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

/** "você A · 85 · TMDB 78", com "via Letterboxd" quando a nota veio de lá. */
@Composable
private fun ScoreLine(item: Item?, t: Title) {
    val s = LocalSettings.current
    val p = AppTheme.palette
    val raw = item?.entry?.scoreRaw ?: 0
    val tmdb = t.voteAverage?.let { (it * 10).toInt() }
    if (raw == 0 && tmdb == null) return
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (raw > 0) {
            Text(t("você "), style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            Box(Modifier.bounceOn(raw)) { ScoreView(raw, s.primaryScore, s.secondaryScore, ScoreSize.Normal) }
            if (item?.entry?.scoreSource == ScoreSource.LETTERBOXD) Text(t("  via Letterboxd"), style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
            if (tmdb != null) Text("  ·  ", style = Mono.style(12), color = p.textTertiary)
        }
        if (tmdb != null) {
            Text("TMDB ", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            Text("$tmdb", style = Mono.style(15, FontWeight.Medium), color = p.text)
        }
    }
}

/** Tabela de informações em duas colunas. */
@Composable
private fun InfoTable(t: Title) {
    val p = AppTheme.palette
    val rows = buildList {
        add(t("Tipo") to t.type.label)
        if (t.type == MediaType.TV) {
            val n = t.seasons.count { it.number >= 1 }
            if (n > 0) add(t("Temporadas") to listOfNotNull(
                if (n == 1) t("1 temporada") else t("{0} temporadas", n),
                t.totalEpisodes.takeIf { it > 0 }?.let { t("{0} ep.", it) },
                runtimeText(t.runtime)?.let { t("{0} cada", it) },
            ).joinToString(" · "))
        } else runtimeText(t.runtime)?.let { add(t("Duração") to it) }
        productionLabel(t)?.let { add(t("Status") to it) }
        Dates.parse(t.releaseDate)?.let { add(t("Estreia") to Dates.full(it)) }
        t.network?.let { add((if (t.type == MediaType.TV) t("Emissora") else t("Estúdio")) to it) }
        if (t.creators.isNotEmpty()) add((if (t.type == MediaType.TV) t("Criação") else t("Direção")) to t.creators.joinToString(", "))
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).enterAnimation(3), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (k, v) ->
            Row {
                Text(k, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.width(96.dp))
                Text(v, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "Onde assistir": logos por Assinatura, Grátis, Aluguel e Compra, com os meus serviços primeiro. */
@Composable
private fun WhereToWatch(t: Title, mine: Set<Int>) {
    val p = AppTheme.palette
    val ctx = LocalContext.current
    SectionHeader(t("Onde assistir"))
    val groups = ProviderRules.grouped(t.providers, mine)
    if (groups.isEmpty()) {
        Text(
            if (t.providersUpdatedAt == 0L) t("Abra com internet para ver onde assistir.") else t("Não disponível em streaming no Brasil."),
            style = MaterialTheme.typography.bodySmall, color = p.textSecondary, modifier = Modifier.padding(horizontal = 20.dp),
        )
        return
    }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.forEach { (kind, list) ->
            Column {
                Text(kind.label, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { pr ->
                        val isMine = pr.id in mine
                        Column(Modifier.width(56.dp).pressable(onClick = { StreamingApps.open(ctx, pr, t.providers?.link) }), horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                TmdbImages.logo(pr.logoPath), pr.name,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(p.surfaceAlt)
                                    .then(if (isMine) Modifier.border(2.dp, p.accent, RoundedCornerShape(12.dp)) else Modifier),
                            )
                            Text(
                                pr.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center,
                                color = if (isMine) p.accent else p.textSecondary, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Synopsis(text: String) {
    var expanded by remember { mutableStateOf(false) }
    SectionHeader(t("Sinopse"))
    Text(
        text, style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 20.dp).animateContentSize().clickable { expanded = !expanded },
    )
}

/** Uma temporada: toque abre os episódios para marcar um a um; ações para a temporada inteira. */
@Composable
private fun SeasonBlock(
    season: SeasonInfo,
    episodes: List<EpisodeInfo>,
    watched: Set<Ep>,
    loading: Boolean,
    lastAired: Ep?,
    ended: Boolean,
    onExpand: () -> Unit,
    onEpisode: (Ep, Boolean) -> Unit,
    onSeason: (Int, Boolean) -> Unit,
) {
    val p = AppTheme.palette
    var open by remember { mutableStateOf(false) }
    val seen = (1..season.episodeCount).count { Ep(season.number, it) in watched }
    val name = if (season.number == 0) t("Especiais") else t("Temporada {0}", season.number)
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open; if (open) onExpand() }.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text("$seen/${season.episodeCount}", style = Mono.style(12), color = if (seen == season.episodeCount) p.accent else p.textSecondary)
            }
            Icon(
                Icons.Default.KeyboardArrowDown, null, tint = p.textTertiary,
                modifier = Modifier.rotate(androidx.compose.animation.core.animateFloatAsState(if (open) 0f else -90f, label = "arrow").value),
            )
        }
        if (open) {
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { onSeason(season.number, true) }) { Text(t("Marcar temporada inteira"), color = p.accent) }
                TextButton(onClick = { onSeason(season.number, false) }) { Text(t("Desmarcar"), color = p.textSecondary) }
            }
            if (episodes.isEmpty() && loading) Text(t("Carregando episódios"), style = MaterialTheme.typography.bodySmall, color = p.textTertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            val list = episodes.ifEmpty { (1..season.episodeCount).map { EpisodeInfo(0, season.number, it) } }
            list.forEach { e ->
                val ep = e.ep
                val on = ep in watched
                val aired = season.number == 0 || dev.viko.sessao.domain.Progress.isAired(ep, lastAired, ended) || Dates.parse(e.airDate)?.let { !it.isAfter(java.time.LocalDate.now()) } == true
                Row(
                    Modifier.fillMaxWidth().clickable { onEpisode(ep, !on) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("E${e.episode}", style = Mono.style(13, FontWeight.Medium), color = if (on) p.accent else p.textSecondary, modifier = Modifier.width(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name.ifBlank { t("Episódio {0}", e.episode) }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (aired) p.text else p.textTertiary)
                        Dates.parse(e.airDate)?.let { Text(Dates.full(it), style = Mono.style(11), color = p.textTertiary) }
                    }
                    Icon(
                        if (on) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (on) t("Visto") else t("Não visto"),
                        tint = if (on) p.accent else p.textTertiary, modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Editar: anotação curta e vezes que vi; remover da lista. */
@Composable
private fun EditDialog(item: Item, onDismiss: () -> Unit, onSave: (String, Int) -> Unit, onRemove: () -> Unit) {
    val p = AppTheme.palette
    var note by remember { mutableStateOf(item.entry.note) }
    var times by remember { mutableStateOf(item.entry.timesWatched.toString()) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Editar")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(note, { note = it.take(500) }, label = { Text(t("Anotação")) }, minLines = 2, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(
                    times, { v -> times = v.filter { it.isDigit() }.take(3) }, label = { Text(t("Vezes que vi")) }, singleLine = true,
                    textStyle = Mono.style(16), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                )
                listOfNotNull(
                    t("Adicionado em {0}", Dates.full(Dates.of(item.entry.addedAt))),
                    item.entry.startedAt?.let { t("Começou em {0}", Dates.full(Dates.of(it))) },
                    item.entry.finishedAt?.let { t("Terminou em {0}", Dates.full(Dates.of(it))) },
                ).forEach { Text(it, style = Mono.style(12), color = p.textSecondary) }
                TextButton(onClick = { confirm = true }) { Text(t("Remover da lista"), color = p.textSecondary) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note.trim(), times.toIntOrNull() ?: item.entry.timesWatched) }) { Text(t("Salvar"), color = p.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancelar"), color = p.textSecondary) } },
        shape = RoundedCornerShape(16.dp),
        containerColor = p.background,
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(t("Remover da lista?")) },
        text = { Text(t("A nota, os episódios marcados e a anotação deste título são apagados.")) },
        confirmButton = { TextButton(onClick = { confirm = false; onRemove() }) { Text(t("Remover"), color = p.accent) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("Cancelar"), color = p.textSecondary) } },
        shape = RoundedCornerShape(16.dp),
        containerColor = p.background,
    )
}
