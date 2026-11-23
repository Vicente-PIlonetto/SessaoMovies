package dev.viko.sessao.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.settings.ListSort
import dev.viko.sessao.domain.Item
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.EmptyState
import dev.viko.sessao.ui.components.EpisodePickerDialog
import dev.viko.sessao.ui.components.GridRow
import dev.viko.sessao.ui.components.ItemGridCell
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.ScorePickerSheet
import dev.viko.sessao.ui.components.SkeletonRows
import dev.viko.sessao.ui.components.StatusHeader
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.coverKey
import dev.viko.sessao.ui.components.pickerStart
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class ListViewModel(c: AppContainer) : ListActionsViewModel(c) {
    val items = c.lists.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

/** Lista completa de um tipo, com todas as categorias na ordem escolhida. */
@Composable
fun ListScreen(nav: Nav, type: MediaType) {
    val vm = appViewModel(key = "list-$type") { ListViewModel(it) }
    val all by vm.items.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    var sort by rememberSaveable { mutableStateOf(s.homeSort) }
    var sortMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf<Item?>(null) }
    var scoring by remember { mutableStateOf<Item?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (type == MediaType.TV) t("Séries") else t("Filmes"), onBack = nav::back) {
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, t("Ordenar")) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    ListSort.entries.forEach {
                        DropdownMenuItem(
                            text = { Text(it.label, color = if (it == sort) AppTheme.palette.accent else AppTheme.palette.text) },
                            onClick = { sort = it; sortMenu = false },
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(t("Buscar na lista")) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, t("Limpar")) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
        )
        val list = all?.filter { it.type == type } ?: return@Column SkeletonRows()
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) list else list.filter { i ->
            val title = i.title ?: return@filter false
            title.name.lowercase().contains(q) || title.originalName?.lowercase()?.contains(q) == true
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (filtered.isEmpty()) item { EmptyState(if (list.isEmpty()) t("Nada na lista ainda.") else t("Nada encontrado.")) }
            s.order(type).forEach { st ->
                val group = sortItems(filtered.filter { it.entry.status == st }, sort)
                if (group.isEmpty()) return@forEach
                item(key = "h-$st") { StatusHeader(st, type, group.size) }
                items(group.chunked(3), key = { "g-${it.first().key}" }) { row ->
                    GridRow(row, 3) { it ->
                        val k = coverKey("list", it.key)
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
