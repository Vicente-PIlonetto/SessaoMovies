package dev.viko.sessao.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.api.TitleSummary
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.AppRefreshBox
import dev.viko.sessao.ui.components.EmptyState
import dev.viko.sessao.ui.components.GridRow
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.MessageBar
import dev.viko.sessao.ui.components.Pill
import dev.viko.sessao.ui.components.SectionHeader
import dev.viko.sessao.ui.components.SkeletonPosterRow
import dev.viko.sessao.ui.components.SummaryCard
import dev.viko.sessao.ui.components.SummaryRow
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.coverKey
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DiscoverSections(
    val trending: List<TitleSummary>,
    val popularTv: List<TitleSummary>,
    val popularMovies: List<TitleSummary>,
    val cinemas: List<TitleSummary>,
    val streaming: List<TitleSummary>,
)

/** Filtros do "Explorar". [services] vazio = sem filtro; [mine] = só nos meus serviços. */
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

    init { load() }

    fun load(force: Boolean = false) = viewModelScope.launch {
        if (sections.value != null && !force) return@launch
        loading.value = true
        try {
            val a = async { c.titles.trending() }
            val b = async { c.titles.popular(MediaType.TV) }
            val m = async { c.titles.popular(MediaType.MOVIE) }
            val n = async { c.titles.nowPlaying() }
            val s = async { c.titles.arrivingStreaming() }
            sections.value = DiscoverSections(a.await().distinctBy { it.key }, b.await().distinctBy { it.key }, m.await().distinctBy { it.key }, n.await().distinctBy { it.key }, s.await().distinctBy { it.key })
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

    /** Sorteia um item da minha lista "Quero ver". */
    fun random(onPick: (dev.viko.sessao.domain.TitleKey) -> Unit) = viewModelScope.launch {
        val planning = c.lists.items.first().filter { it.entry.status == WatchStatus.PLANNING }
        val pick = planning.randomOrNull()
        if (pick == null) message.value = t("Sua lista Quero ver está vazia.") else onPick(pick.key)
    }
}

@Composable
fun DiscoverScreen(nav: Nav) {
    val vm = appViewModel { DiscoverViewModel(it) }
    val sections by vm.sections.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Descobrir")) {
            IconButton(onClick = { vm.random { nav.detail(it) } }) { Icon(Icons.Outlined.Casino, t("Sortear da lista Quero ver")) }
        }
        message?.let { MessageBar(it, t("OK")) { vm.message.value = null } }
        error?.let { MessageBar(it, t("Tentar de novo")) { vm.load(force = true) } }
        AppRefreshBox(isRefreshing = loading, onRefresh = { vm.load(force = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                val sec = sections
                if (sec == null) {
                    if (error == null) items(3) { SkeletonPosterRow() }
                    return@LazyColumn
                }
                fun row(title: String, list: List<TitleSummary>, prefix: String) {
                    if (list.isEmpty()) return
                    item(key = prefix) {
                        SectionHeader(title)
                        SummaryRow(list, prefix) { m, k -> nav.detail(m.key, k) }
                    }
                }
                row(t("Em alta na semana"), sec.trending, "tr")
                row(t("Séries populares"), sec.popularTv, "ptv")
                row(t("Filmes populares"), sec.popularMovies, "pmv")
                row(t("Estreias nos cinemas"), sec.cinemas, "cin")
                row(t("Chegando ao streaming"), sec.streaming, "str")

                item(key = "explore") {
                    SectionHeader(t("Explorar"))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { Pill(t("Filmes"), filter.type == MediaType.MOVIE, { vm.setFilter(filter.copy(type = MediaType.MOVIE, genre = null)) }) }
                        item { Pill(t("Séries"), filter.type == MediaType.TV, { vm.setFilter(filter.copy(type = MediaType.TV, genre = null)) }) }
                        if (s.myServices.isNotEmpty()) item {
                            Pill(t("Nos meus serviços"), filter.mine && filter.service == null, { vm.setFilter(filter.copy(mine = !filter.mine, service = null)) })
                        }
                        items(s.myServices, key = { "sv${it.id}" }) { sv ->
                            Pill(sv.name, filter.service == sv.id, { vm.setFilter(filter.copy(service = if (filter.service == sv.id) null else sv.id, mine = false)) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
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
                        Box(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { vm.explore(reset = false) }, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Text(t("Mais resultados"), color = AppTheme.palette.accent)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
