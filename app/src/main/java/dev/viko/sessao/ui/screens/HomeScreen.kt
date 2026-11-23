package dev.viko.sessao.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.settings.ListSort
import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.Item
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.AppRefreshBox
import dev.viko.sessao.ui.components.EmptyState
import dev.viko.sessao.ui.components.EpisodePickerDialog
import dev.viko.sessao.ui.components.GridRow
import dev.viko.sessao.ui.components.ItemGridCell
import dev.viko.sessao.ui.components.LinkRow
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.MessageBar
import dev.viko.sessao.ui.components.PagerSegmented
import dev.viko.sessao.ui.components.ScorePickerSheet
import dev.viko.sessao.ui.components.SkeletonRows
import dev.viko.sessao.ui.components.StatusIcon
import dev.viko.sessao.ui.components.ThinDivider
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.coverKey
import dev.viko.sessao.ui.components.enterAnimation
import dev.viko.sessao.ui.components.pickerStart
import dev.viko.sessao.ui.components.pressable
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono
import dev.viko.sessao.ui.theme.statusColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ordena itens da lista conforme a escolha do usuário. */
fun sortItems(list: List<Item>, sort: ListSort): List<Item> = when (sort) {
    ListSort.NEW_FIRST -> list.sortedWith(compareByDescending<Item> { it.hasNew }.thenByDescending { it.entry.updatedAt })
    ListSort.RECENT -> list.sortedByDescending { it.entry.updatedAt }
    ListSort.ADDED -> list.sortedByDescending { it.entry.addedAt }
    ListSort.TITLE -> list.sortedBy { it.title?.name?.lowercase() ?: "" }
    ListSort.SCORE -> list.sortedWith(compareByDescending<Item> { it.entry.scoreRaw }.thenBy { it.title?.name?.lowercase() ?: "" })
    ListSort.YEAR -> list.sortedByDescending { it.title?.year ?: 0 }
}

/** Ações da lista usadas pelo Início e pela lista completa. */
open class ListActionsViewModel(protected val c: AppContainer) : ViewModel() {
    val message = MutableStateFlow<String?>(null)
    fun increment(key: TitleKey) = viewModelScope.launch { c.lists.increment(key) }
    fun markUpTo(key: TitleKey, ep: Ep) = viewModelScope.launch {
        if (ep.season == 0) c.lists.setEpisode(key, ep, true) else c.lists.markUpTo(key, ep)
    }
    fun movieWatched(key: TitleKey) = viewModelScope.launch { c.lists.setStatus(key, WatchStatus.COMPLETED) }
    fun setScore(key: TitleKey, raw: Int) = viewModelScope.launch { c.lists.setScore(key, raw) }
}

class HomeViewModel(c: AppContainer) : ListActionsViewModel(c) {
    val items = c.lists.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val refreshing = MutableStateFlow(false)

    fun refresh() = viewModelScope.launch {
        if (refreshing.value) return@launch
        refreshing.value = true
        try {
            val r = c.refresh.refreshAll(notify = false)
            message.value = if (r.failed > 0 && r.updated == 0) (r.error ?: REFRESH_ERROR) else null
        } finally {
            refreshing.value = false
        }
    }

    fun setSort(v: ListSort) = viewModelScope.launch { c.settings.setHomeSort(v) }
}

@Composable
fun HomeScreen(nav: Nav) {
    val vm = appViewModel { HomeViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val pager = rememberPagerState { 2 }
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Início")) {
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, t("Ordenar")) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    ListSort.entries.forEach {
                        DropdownMenuItem(
                            text = { Text(it.label, color = if (it == s.homeSort) AppTheme.palette.accent else AppTheme.palette.text) },
                            onClick = { vm.setSort(it); sortMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = nav::search) { Icon(Icons.Outlined.Search, t("Buscar")) }
            IconButton(onClick = nav::notifications) { Icon(Icons.Outlined.Notifications, t("Notificações")) }
        }
        message?.let { MessageBar(it, t("OK")) { vm.message.value = null } }
        PagerSegmented(pager, listOf(t("Séries"), t("Filmes")), Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        AppRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
                val type = if (page == 0) MediaType.TV else MediaType.MOVIE
                HomeList(nav, vm, type, items?.filter { it.type == type })
            }
        }
    }
}

@Composable
private fun HomeList(nav: Nav, vm: ListActionsViewModel, type: MediaType, list: List<Item>?) {
    val s = LocalSettings.current
    val tabs = s.homeTabs(type)
    var chosen by rememberSaveable(type) { mutableStateOf<WatchStatus?>(null) }
    // Sem escolha (ou se a aba escolhida foi escondida), abre na primeira da ordem.
    val selected = chosen?.takeIf { it in tabs } ?: tabs.first()
    val grouped = remember(list, tabs, s.homeSort) { list?.let { l -> tabs.associateWith { st -> sortItems(l.filter { it.entry.status == st }, s.homeSort) } } }
    var picker by remember { mutableStateOf<Item?>(null) }
    var scoring by remember { mutableStateOf<Item?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            LinkRow(if (type == MediaType.TV) t("Lista completa de séries") else t("Lista completa de filmes"), list?.size?.toString()) { nav.list(type) }
            ThinDivider()
        }
        if (list == null || grouped == null) { item { SkeletonRows() }; return@LazyColumn }
        item(key = "tabs") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(tabs, key = { _, st -> st.name }) { _, st ->
                    StatusChip(st, type, grouped[st].orEmpty().size, st == selected) { chosen = st }
                }
            }
        }
        val items = grouped[selected].orEmpty()
        if (list.isEmpty()) item(key = "empty") {
            EmptyState(if (type == MediaType.TV) t("Nada em andamento. Busque um título para começar.") else t("Nenhum filme na lista. Busque um título para começar."))
        } else if (items.isEmpty()) item(key = "empty-$selected") { EmptyState(t("Nada aqui.")) }
        itemsIndexed(items.chunked(3), key = { _, row -> "g-${selected.name}-${row.first().key}" }) { i, row ->
            Box(Modifier.animateItem().enterAnimation(i)) {
                GridRow(row, 3) { it ->
                    val k = coverKey("home", it.key)
                    ItemGridCell(
                        it, k,
                        onClick = { nav.detail(it.key, k) },
                        onIncrement = { vm.increment(it.key) },
                        onPickEpisode = { picker = it },
                        onMovieWatched = { vm.movieWatched(it.key) },
                        onScore = { scoring = it },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    picker?.let { item ->
        EpisodePickerDialog(item.seasons, item.watched, item.pickerStart(), onDismiss = { picker = null }) { ep ->
            vm.markUpTo(item.key, ep); picker = null
        }
    }
    scoring?.let { item ->
        ScorePickerSheet(
            initialRaw = item.entry.scoreRaw, system = s.primaryScore, subtitle = item.title?.name,
            onDismiss = { scoring = null },
            onSave = { vm.setScore(item.key, it.raw); scoring = null },
        )
    }
}

/** Chip de status: ícone, nome e quantidade; o escolhido fica na cor do status. */
@Composable
fun StatusChip(st: WatchStatus, type: MediaType, count: Int, selected: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    val col = statusColor(st)
    val bg by androidx.compose.animation.animateColorAsState(if (selected) col.copy(alpha = 0.2f) else p.surfaceAlt, label = "chip")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).pressable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(st, if (selected) col else p.textSecondary, 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(st.label(type), style = MaterialTheme.typography.labelLarge, color = if (selected) col else p.text)
        Spacer(Modifier.width(6.dp))
        Text("$count", style = Mono.style(12), color = if (selected) col else p.textTertiary)
    }
}
