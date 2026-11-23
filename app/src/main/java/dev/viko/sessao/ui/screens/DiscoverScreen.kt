package dev.viko.sessao.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.api.TitleSummary
import dev.viko.sessao.data.api.TmdbImages
import dev.viko.sessao.data.api.toTitle
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.AppRefreshBox
import dev.viko.sessao.ui.components.EmptyState
import dev.viko.sessao.ui.components.GridRow
import dev.viko.sessao.ui.components.LocalQuickStatus
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.LocalStatusMap
import dev.viko.sessao.ui.components.MessageBar
import dev.viko.sessao.ui.components.Pill
import dev.viko.sessao.ui.components.SectionHeader
import dev.viko.sessao.ui.components.SkeletonBox
import dev.viko.sessao.ui.components.SkeletonPosterRow
import dev.viko.sessao.ui.components.SkeletonRows
import dev.viko.sessao.ui.components.StatusLabel
import dev.viko.sessao.ui.components.SummaryCard
import dev.viko.sessao.ui.components.SummaryRow
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.coverKey
import dev.viko.sessao.ui.components.enterAnimation
import dev.viko.sessao.ui.components.pressable
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DiscoverSections(
    val trending: List<TitleSummary>,
    val popularTv: List<TitleSummary>,
    val popularMovies: List<TitleSummary>,
    val cinemas: List<TitleSummary>,
    val streaming: List<TitleSummary>,
    val mine: List<TitleSummary>,
)

/** Filtros do "Explorar". [mine] = só nos meus serviços. */
data class DiscoverFilter(val type: MediaType = MediaType.MOVIE, val genre: Int? = null, val service: Int? = null, val mine: Boolean = false)

class DiscoverViewModel(private val c: AppContainer) : ViewModel() {
    val sections = MutableStateFlow<DiscoverSections?>(null)
    val error = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)
    val filter = MutableStateFlow(DiscoverFilter())
    val genres = MutableStateFlow<Map<MediaType, List<Pair<Int, String>>>>(emptyMap())
    val results = MutableStateFlow<List<TitleSummary>?>(null)
    val message = MutableStateFlow<String?>(null)
    private var page = 1

    // Busca dentro do Descobrir.
    val query = MutableStateFlow("")
    val searchType = MutableStateFlow<MediaType?>(null)
    val searchResults = MutableStateFlow<List<TitleSummary>?>(null)
    val searching = MutableStateFlow(false)
    private var searchJob: Job? = null
    private var searchPage = 1

    init { load() }

    fun load(force: Boolean = false) = viewModelScope.launch {
        if (sections.value != null && !force) return@launch
        loading.value = true
        try {
            val mineIds = c.settings.current().myServiceIds
            val a = async { c.titles.trending() }
            val b = async { c.titles.popular(MediaType.TV) }
            val m = async { c.titles.popular(MediaType.MOVIE) }
            val n = async { c.titles.nowPlaying() }
            val s = async { c.titles.arrivingStreaming() }
            val my = async {
                if (mineIds.isEmpty()) emptyList()
                else runCatching { (c.titles.discover(MediaType.TV, null, mineIds) + c.titles.discover(MediaType.MOVIE, null, mineIds)).sortedByDescending { it.voteAverage ?: 0.0 } }.getOrDefault(emptyList())
            }
            sections.value = DiscoverSections(
                a.await().distinctBy { it.key }, b.await().distinctBy { it.key }, m.await().distinctBy { it.key },
                n.await().distinctBy { it.key }, s.await().distinctBy { it.key }, my.await().distinctBy { it.key }.take(20),
            )
            error.value = null
            runCatching { genres.value = mapOf(MediaType.MOVIE to c.titles.genres(MediaType.MOVIE), MediaType.TV to c.titles.genres(MediaType.TV)) }
            explore(reset = true)
        } catch (e: Exception) {
            error.value = e.toUserMessage()
        } finally {
            loading.value = false
        }
    }

    fun setFilter(f: DiscoverFilter) { filter.value = f; explore(reset = true) }

    fun explore(reset: Boolean) = viewModelScope.launch {
        if (reset) { page = 1; results.value = null } else page++
        val f = filter.value
        val providers = when {
            f.service != null -> setOf(f.service)
            f.mine -> c.settings.current().myServiceIds
            else -> emptySet()
        }
        try {
            val r = c.titles.discover(f.type, f.genre, providers, page)
            results.value = (if (reset) r else results.value.orEmpty() + r).distinctBy { it.key }
        } catch (e: Exception) {
            error.value = e.toUserMessage()
            if (reset) results.value = emptyList()
        }
    }

    fun onQuery(q: String) {
        query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { searchResults.value = null; return }
        searchJob = viewModelScope.launch {
            delay(400)
            searchPage = 1
            runSearch(reset = true)
        }
    }

    fun moreResults() = viewModelScope.launch { searchPage++; runSearch(reset = false) }

    private suspend fun runSearch(reset: Boolean) {
        searching.value = true
        try {
            val r = c.titles.search(query.value.trim(), searchPage)
            searchResults.value = (if (reset) r else searchResults.value.orEmpty() + r).distinctBy { it.key }
            error.value = null
        } catch (e: Exception) {
            error.value = e.toUserMessage()
            if (reset) searchResults.value = emptyList()
        } finally {
            searching.value = false
        }
    }

    /** Sorteia um item da minha lista "Quero ver". */
    fun random(onPick: (TitleKey) -> Unit) = viewModelScope.launch {
        val planning = c.lists.items.first().filter { it.entry.status == WatchStatus.PLANNING }
        val pick = planning.randomOrNull()
        if (pick == null) message.value = t("Sua lista Quero ver está vazia.") else onPick(pick.key)
    }
}

/** Cores dos blocos de gênero, sempre na mesma ordem. */
private val GENRE_COLORS = listOf(0xFFE2B252, 0xFF6FAEE8, 0xFFEC6F82, 0xFF7CC08E, 0xFFAD96E6, 0xFFD98A66, 0xFF4FD1BE, 0xFFE58AAE)

@Composable
fun DiscoverScreen(nav: Nav) {
    val vm = appViewModel { DiscoverViewModel(it) }
    val sections by vm.sections.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Descobrir")) {
            IconButton(onClick = { vm.random { nav.detail(it) } }) { Icon(Icons.Outlined.Casino, t("Sortear da lista Quero ver")) }
        }
        SearchBar(query, vm::onQuery)
        message?.let { MessageBar(it, t("OK")) { vm.message.value = null } }
        error?.let { MessageBar(it, t("Tentar de novo")) { if (query.isBlank()) vm.load(force = true) else vm.onQuery(query) } }
        if (query.isNotBlank()) SearchResults(vm, nav)
        else AppRefreshBox(isRefreshing = loading && sections != null, onRefresh = { vm.load(force = true) }, modifier = Modifier.fillMaxSize()) {
            val sec = sections
            if (sec == null) {
                Column {
                    SkeletonBox(null, 190.dp, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), radius = 20.dp)
                    if (error == null) repeat(2) { SkeletonPosterRow() }
                }
            } else DiscoverBody(vm, nav, sec)
        }
    }
}

/** Barra de busca em pílula; buscar aqui mostra filmes e séries juntos. */
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit) {
    val p = AppTheme.palette
    val fm = LocalFocusManager.current
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(50)).background(p.surfaceAlt).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = p.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(t("Buscar filmes e séries"), style = MaterialTheme.typography.bodyLarge, color = p.textTertiary)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.text),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { fm.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            IconButton(onClick = { onQuery(""); fm.clearFocus() }) { Icon(Icons.Default.Close, t("Limpar"), tint = p.textSecondary) }
        }
    }
}

@Composable
private fun SearchResults(vm: DiscoverViewModel, nav: Nav) {
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val type by vm.searchType.collectAsStateWithLifecycle()
    val list = results?.filter { type == null || it.key.type == type }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(t("Tudo"), type == null, { vm.searchType.value = null })
                Pill(t("Filmes"), type == MediaType.MOVIE, { vm.searchType.value = MediaType.MOVIE })
                Pill(t("Séries"), type == MediaType.TV, { vm.searchType.value = MediaType.TV })
            }
        }
        when {
            list == null -> item { SkeletonRows() }
            list.isEmpty() && !searching -> item { EmptyState(t("Nada encontrado.")) }
            else -> {
                items(list.chunked(3), key = { "s-${it.first().key}" }) { row ->
                    GridRow(row, 3) { m ->
                        val k = coverKey("dsearch", m.key)
                        BoxWithConstraints(Modifier.fillMaxWidth()) { SummaryCard(m, { nav.detail(m.key, k) }, width = maxWidth, sharedKey = k) }
                    }
                }
                if ((results?.size ?: 0) % 20 == 0) item {
                    TextButton(onClick = { vm.moreResults() }, enabled = !searching, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(t("Mais resultados"), color = AppTheme.palette.accent)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DiscoverBody(vm: DiscoverViewModel, nav: Nav, sec: DiscoverSections) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Posição do "Explorar" na lista, para os blocos de gênero rolarem até lá.
    val rows = listOf(
        Triple(t("Nos seus serviços"), sec.mine, "mine"),
        Triple(t("Séries populares"), sec.popularTv, "ptv"),
        Triple(t("Filmes populares"), sec.popularMovies, "pmv"),
        Triple(t("Estreias nos cinemas"), sec.cinemas, "cin"),
        Triple(t("Chegando ao streaming"), sec.streaming, "str"),
    ).filter { it.second.isNotEmpty() }
    val genreList = genres[MediaType.MOVIE].orEmpty()
    val exploreIndex = 2 + rows.size + (if (genreList.isNotEmpty()) 1 else 0)

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item(key = "hero") { Hero(sec.trending.filter { it.backdropPath != null }.take(8), nav) }
        item(key = "top") {
            SectionHeader(t("Top 10 da semana"))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(sec.trending.take(10), key = { _, it -> "top-${it.key}" }) { i, m ->
                    val k = coverKey("top", m.key)
                    RankedCard(i + 1, m, k) { nav.detail(m.key, k) }
                }
            }
        }
        rows.forEach { (title, list, prefix) ->
            item(key = prefix) {
                SectionHeader(title)
                SummaryRow(list, prefix) { m, k -> nav.detail(m.key, k) }
            }
        }
        if (genreList.isNotEmpty()) item(key = "genres") {
            SectionHeader(t("Gêneros"))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(genreList, key = { _, g -> "gt${g.first}" }) { i, (id, name) ->
                    GenreTile(name, Color(GENRE_COLORS[i % GENRE_COLORS.size])) {
                        vm.setFilter(filter.copy(type = MediaType.MOVIE, genre = id))
                        scope.launch { listState.animateScrollToItem(exploreIndex) }
                    }
                }
            }
        }
        item(key = "explore") {
            SectionHeader(t("Explorar"))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Pill(t("Filmes"), filter.type == MediaType.MOVIE, { vm.setFilter(filter.copy(type = MediaType.MOVIE, genre = null)) }) }
                item { Pill(t("Séries"), filter.type == MediaType.TV, { vm.setFilter(filter.copy(type = MediaType.TV, genre = null)) }) }
                if (s.myServices.isNotEmpty()) item {
                    Pill(t("Nos meus serviços"), filter.mine && filter.service == null, { vm.setFilter(filter.copy(mine = !filter.mine, service = null)) })
                }
            }
            if (s.myServices.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.myServices, key = { "sv${it.id}" }) { sv ->
                        ServiceChip(sv.name, TmdbImages.logo(sv.logoPath), filter.service == sv.id) {
                            vm.setFilter(filter.copy(service = if (filter.service == sv.id) null else sv.id, mine = false))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Pill(t("Todos os gêneros"), filter.genre == null, { vm.setFilter(filter.copy(genre = null)) }) }
                items(genres[filter.type].orEmpty(), key = { "g${it.first}" }) { (id, name) ->
                    Pill(name, filter.genre == id, { vm.setFilter(filter.copy(genre = if (filter.genre == id) null else id)) })
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        val list = results
        if (list == null) item { SkeletonPosterRow() }
        else if (list.isEmpty()) item { EmptyState(t("Nada com esses filtros.")) }
        else {
            items(list.chunked(3), key = { "e-${it.first().key}" }) { r ->
                GridRow(r, 3) { m ->
                    val k = coverKey("ex", m.key)
                    BoxWithConstraints(Modifier.fillMaxWidth()) { SummaryCard(m, { nav.detail(m.key, k) }, width = maxWidth, sharedKey = k) }
                }
            }
            if (list.size % 20 == 0) item {
                TextButton(onClick = { vm.explore(reset = false) }, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(t("Mais resultados"), color = AppTheme.palette.accent)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Destaque: os títulos em alta, com o fundo grande, deslizando para o lado. */
@Composable
private fun Hero(items: List<TitleSummary>, nav: Nav) {
    if (items.isEmpty()) return
    val p = AppTheme.palette
    val pager = rememberPagerState { items.size }
    val quick = LocalQuickStatus.current
    val statuses = LocalStatusMap.current
    Column(Modifier.padding(top = 8.dp)) {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { i ->
            val m = items[i]
            // A página que sai encolhe um pouco.
            val off = (pager.currentPage - i + pager.currentPageOffsetFraction).let { kotlin.math.abs(it) }.coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().height(200.dp)
                    .graphicsLayer { val k = 1f - 0.06f * off; scaleX = k; scaleY = k; alpha = 1f - 0.3f * off }
                    .clip(RoundedCornerShape(20.dp)).background(p.surfaceAlt)
                    .pressable(onClick = { nav.detail(m.key) }, onLongClick = { quick(m.toTitle()) }, scaleTo = 0.98f),
            ) {
                AsyncImage(TmdbImages.backdrop(m.backdropPath), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(
                        listOfNotNull(m.key.type.label, m.year?.toString(), m.voteAverage?.let { "${(it * 10).toInt()}%" }).joinToString(" · "),
                        style = Mono.style(12), color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(m.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    statuses[m.key]?.let { Box(Modifier.padding(top = 4.dp)) { StatusLabel(it, m.key.type) } }
                }
                Text(
                    "${i + 1}/${items.size}", style = Mono.style(11, FontWeight.Medium), color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        // Pontos da página atual.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val on = pager.currentPage == i
                val c by animateColorAsState(if (on) p.accent else p.divider, label = "dot")
                val w by androidx.compose.animation.core.animateDpAsState(if (on) 18.dp else 6.dp, label = "dotw")
                Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(CircleShape).background(c))
            }
        }
    }
}

/** Pôster com a posição grande ao lado, em mono. */
@Composable
private fun RankedCard(rank: Int, m: TitleSummary, sharedKey: String, onClick: () -> Unit) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.enterAnimation(rank - 1)) {
        Text(
            "$rank", style = Mono.style(56, FontWeight.SemiBold), color = p.textTertiary.copy(alpha = 0.6f),
            modifier = Modifier.offset(x = 6.dp, y = (-28).dp),
        )
        SummaryCard(m, onClick, width = 100.dp, sharedKey = sharedKey)
    }
}

/** Bloco de gênero: nome sobre uma cor chapada, que leva ao Explorar já filtrado. */
@Composable
private fun GenreTile(name: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.width(132.dp).height(64.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).pressable(onClick = onClick).padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(color))
        Text(name, style = MaterialTheme.typography.labelLarge, color = AppTheme.palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Serviço com logo, para filtrar o Explorar. */
@Composable
private fun ServiceChip(name: String, logo: String?, selected: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    val border by animateColorAsState(if (selected) p.accent else Color.Transparent, label = "svc")
    Column(Modifier.width(60.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            logo, name,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(p.surfaceAlt).border(2.dp, border, RoundedCornerShape(12.dp)),
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = if (selected) p.accent else p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}
