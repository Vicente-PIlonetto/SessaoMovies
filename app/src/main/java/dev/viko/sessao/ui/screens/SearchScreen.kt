package dev.viko.sessao.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.api.TitleSummary
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.EmptyState
import dev.viko.sessao.ui.components.MessageBar
import dev.viko.sessao.ui.components.SkeletonRows
import dev.viko.sessao.ui.components.SummaryListRow
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.coverKey
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<TitleSummary>?>(null)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var page = 1
    private var job: Job? = null

    fun onQuery(q: String) {
        query.value = q
        job?.cancel()
        if (q.isBlank()) { results.value = null; error.value = null; return }
        job = viewModelScope.launch {
            delay(400)
            page = 1
            load(q, reset = true)
        }
    }

    fun more() = viewModelScope.launch { page++; load(query.value, reset = false) }

    private suspend fun load(q: String, reset: Boolean) {
        loading.value = true
        try {
            val r = c.titles.search(q.trim(), page)
            results.value = (if (reset) r else results.value.orEmpty() + r).distinctBy { it.key }
            error.value = null
        } catch (e: Exception) {
            error.value = e.toUserMessage()
        } finally {
            loading.value = false
        }
    }
}

@Composable
fun SearchScreen(nav: Nav) {
    val vm = appViewModel { SearchViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Buscar"), onBack = nav::back)
        OutlinedTextField(
            value = query, onValueChange = vm::onQuery, singleLine = true,
            placeholder = { Text(t("Filme ou série")) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Default.Close, t("Limpar")) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { fm.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).focusRequester(focus),
            shape = RoundedCornerShape(12.dp),
        )
        error?.let { MessageBar(it) }
        val list = results
        when {
            list == null && loading -> SkeletonRows()
            list == null -> EmptyState(t("Busque pelo nome em português ou no original."))
            list.isEmpty() && !loading -> EmptyState(t("Nada encontrado."))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.key.toString() }) { r ->
                    val k = coverKey("search", r.key)
                    SummaryListRow(r, k) { nav.detail(r.key, k) }
                }
                if (list.size >= 20 && list.size % 20 == 0) item {
                    TextButton(onClick = { vm.more() }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(t("Mais resultados"), color = AppTheme.palette.accent)
                    }
                }
            }
        }
    }
}
