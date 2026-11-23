package dev.viko.sessao.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.Dates
import dev.viko.sessao.ui.components.EmptyState
import dev.viko.sessao.ui.components.LinkRow
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.pressable
import dev.viko.sessao.ui.nav.LocalContainer
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono

/** Avisos já enviados pela atualização diária (episódio novo, chegou num serviço meu). */
@Composable
fun NotificationsScreen(nav: Nav) {
    val c = LocalContainer.current
    val p = AppTheme.palette
    val list by c.db.misc().observeNotifications().collectAsStateWithLifecycle(initialValue = null)
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Notificações"), onBack = nav::back)
        val l = list ?: return@Column
        LazyColumn(Modifier.fillMaxSize()) {
            if (l.isEmpty()) item { EmptyState(t("Nenhum aviso ainda. Ligue os avisos nas Configurações.")) }
            items(l, key = { it.id }) { n ->
                val key = if (n.type != null && n.tmdbId != null) TitleKey(n.type, n.tmdbId) else null
                Column(
                    Modifier.fillMaxWidth().pressable(onClick = { key?.let { nav.detail(it) } }, scaleTo = 0.98f).padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(n.text, style = MaterialTheme.typography.bodyLarge)
                    Text(Dates.relative(Dates.of(n.at)) + " · " + Dates.full(Dates.of(n.at)), style = Mono.style(12), color = p.textSecondary)
                }
            }
            item { LinkRow(t("Configurar avisos")) { nav.settings("notificacoes") } }
        }
    }
}
