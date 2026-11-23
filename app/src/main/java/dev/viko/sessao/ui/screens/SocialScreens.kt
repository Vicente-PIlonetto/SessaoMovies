package dev.viko.sessao.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.viko.sessao.AppContainer
import dev.viko.sessao.data.api.TmdbImages
import dev.viko.sessao.data.trakt.FeedRow
import dev.viko.sessao.data.trakt.FeedRules
import dev.viko.sessao.data.trakt.Profile
import dev.viko.sessao.data.trakt.TraktUser
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.AppRefreshBox
import dev.viko.sessao.ui.components.Cover
import dev.viko.sessao.ui.components.Dates
import dev.viko.sessao.ui.components.EmptyState
import dev.viko.sessao.ui.components.LinkRow
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.MessageBar
import dev.viko.sessao.ui.components.PagerSegmented
import dev.viko.sessao.ui.components.ScoreSize
import dev.viko.sessao.ui.components.ScoreView
import dev.viko.sessao.ui.components.SectionHeader
import dev.viko.sessao.ui.components.SkeletonRows
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.coverKey
import dev.viko.sessao.ui.components.enterAnimation
import dev.viko.sessao.ui.components.pressable
import dev.viko.sessao.ui.nav.LocalContainer
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** "agora", "há 5 min", "há 3 h", "ontem", "há 4 d" ou a data. */
fun timeAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    val s = (now - ms) / 1000
    return when {
        s < 60 -> t("agora")
        s < 3600 -> t("há {0} min", s / 60)
        s < 24 * 3600 -> t("há {0} h", s / 3600)
        s < 48 * 3600 -> t("ontem")
        s < 7 * 24 * 3600 -> t("há {0} d", s / 86400)
        else -> Dates.full(Dates.of(ms))
    }
}

// ---------------- Aba Social ----------------

class SocialViewModel(private val c: AppContainer) : ViewModel() {
    val feed = MutableStateFlow<List<FeedRow>?>(null)
    val following = MutableStateFlow<List<TraktUser>?>(null)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var job: Job? = null

    fun load(force: Boolean = false) {
        if (c.trakt.session.value == null) return
        if (feed.value != null && !force) return
        job?.cancel()
        job = viewModelScope.launch {
            loading.value = true
            try {
                following.value = c.social.following()
                c.social.feed { partial -> feed.value = partial }.also { feed.value = it }
                error.value = null
            } catch (e: Exception) {
                error.value = e.toUserMessage()
                if (feed.value == null) feed.value = emptyList()
            } finally {
                loading.value = false
            }
        }
    }
}

@Composable
fun SocialScreen(nav: Nav) {
    val c = LocalContainer.current
    val vm = appViewModel { SocialViewModel(it) }
    val session by c.trakt.session.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 2 }
    LaunchedEffect(session) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Social")) {
            if (session != null) {
                IconButton(onClick = nav::userSearch) { Icon(Icons.Outlined.PersonSearch, t("Buscar pessoas")) }
            }
        }
        if (session == null) {
            EmptyState(t("Entre com a sua conta do Trakt para ver o que as pessoas que você segue estão vendo."))
            LinkRow(t("Entrar no Trakt")) { nav.settings("trakt") }
            return@Column
        }
        error?.let { MessageBar(it, t("Tentar de novo")) { vm.load(force = true) } }
        PagerSegmented(pager, listOf(t("Atividade"), t("Seguindo")), Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page == 0) FeedPage(vm, nav) else FollowingPage(vm, nav)
        }
    }
}

@Composable
private fun FeedPage(vm: SocialViewModel, nav: Nav) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val following by vm.following.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    AppRefreshBox(isRefreshing = loading && feed != null, onRefresh = { vm.load(force = true) }, modifier = Modifier.fillMaxSize()) {
        val list = feed
        LazyColumn(Modifier.fillMaxSize()) {
            when {
                list == null -> item { SkeletonRows() }
                list.isEmpty() && following?.isEmpty() == true -> item {
                    EmptyState(t("Você ainda não segue ninguém. Busque pessoas pela lupa."))
                }
                list.isEmpty() && !loading -> item { EmptyState(t("Ninguém que você segue viu algo recentemente.")) }
                else -> items(list, key = { "${it.entry.user.username}-${it.entry.key}-${it.entry.at}" }) { row ->
                    FeedItem(row, showUser = true, nav = nav)
                }
            }
            if (loading && !list.isNullOrEmpty()) item {
                Text(t("Carregando mais..."), style = MaterialTheme.typography.bodySmall, color = AppTheme.palette.textTertiary, modifier = Modifier.padding(20.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FollowingPage(vm: SocialViewModel, nav: Nav) {
    val following by vm.following.collectAsStateWithLifecycle()
    val list = following ?: return SkeletonRows()
    LazyColumn(Modifier.fillMaxSize()) {
        if (list.isEmpty()) item { EmptyState(t("Você ainda não segue ninguém. Busque pessoas pela lupa.")) }
        items(list, key = { it.username }) { u -> UserRow(u) { nav.profile(u.ids.slug ?: u.username) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape).background(AppTheme.palette.surfaceAlt))
}

/** Pessoa: foto, nome e @usuário. */
@Composable
private fun UserRow(u: TraktUser, onClick: () -> Unit) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick, scaleTo = 0.98f).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(u.images?.avatar?.full, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(u.name?.takeIf { it.isNotBlank() } ?: u.username, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@" + u.username + if (u.private) t(" · privado") else "", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        }
    }
}

/** Linha do feed: quem viu, o quê, e quando. Tocar no pôster abre o título; na foto, o perfil. */
@Composable
private fun FeedItem(row: FeedRow, showUser: Boolean, nav: Nav) {
    val p = AppTheme.palette
    val e = row.entry
    val k = coverKey("feed${e.user.username}${e.at}", e.key)
    Row(
        Modifier.fillMaxWidth().pressable(onClick = { nav.detail(e.key, k) }, scaleTo = 0.98f).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showUser) {
            Box(Modifier.pressable(onClick = { nav.profile(e.user.ids.slug ?: e.user.username) })) { Avatar(e.user.images?.avatar?.full, 36.dp) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (showUser) Text(
                (e.user.name?.takeIf { it.isNotBlank() } ?: e.user.username) + " " + (if (e.episodes.size > 1) t("viu {0} episódios", e.episodes.size) else t("viu")),
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(if (e.key.type == MediaType.MOVIE) t("Filme") else FeedRules.episodesLabel(e.episodes), timeAgo(e.at)).joinToString(" · "),
                style = Mono.style(12), color = p.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Cover(TmdbImages.poster(row.posterPath, "w185"), 44.dp, color = row.posterColor, sharedKey = k)
    }
}

// ---------------- Perfil ----------------

class ProfileViewModel(private val c: AppContainer, private val slug: String) : ViewModel() {
    val profile = MutableStateFlow<Profile?>(null)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    init { load() }

    fun load() = viewModelScope.launch {
        try { profile.value = c.social.profile(slug); error.value = null } catch (e: Exception) { error.value = e.toUserMessage() }
    }

    fun toggleFollow() = viewModelScope.launch {
        val p = profile.value ?: return@launch
        busy.value = true
        try {
            c.social.follow(slug, !p.following)
            profile.value = p.copy(following = !p.following)
        } catch (e: Exception) { error.value = e.toUserMessage() } finally { busy.value = false }
    }
}

@Composable
fun ProfileScreen(nav: Nav, slug: String) {
    val vm = appViewModel(key = "profile-$slug") { ProfileViewModel(it, slug) }
    val profile by vm.profile.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    val p = AppTheme.palette
    val pr = profile
    if (pr == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("@$slug", onBack = nav::back)
            error?.let { MessageBar(it, t("Tentar de novo")) { vm.load() } } ?: SkeletonRows()
        }
        return
    }
    val u = pr.user
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                IconButton(onClick = nav::back, modifier = Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Voltar")) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(100.dp).clip(CircleShape).background(p.accent).padding(3.dp).clip(CircleShape).background(p.background).padding(3.dp)) {
                        Avatar(u.images?.avatar?.full, 88.dp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(u.name?.takeIf { it.isNotBlank() } ?: u.username, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull("@" + u.username, u.location?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = Mono.style(13), color = p.textSecondary,
                    )
                    u.about?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (pr.isMe) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = nav::customize, shape = RoundedCornerShape(50)) { Text(t("Personalizar"), color = p.text) }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        if (pr.following) OutlinedButton(onClick = vm::toggleFollow, enabled = !busy, shape = RoundedCornerShape(50)) { Text(t("Seguindo"), color = p.text) }
                        else Button(
                            onClick = vm::toggleFollow, enabled = !busy, shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.background),
                        ) { Text(t("Seguir")) }
                    }
                }
            }
        }
        error?.let { item { MessageBar(it) } }
        if (u.private && !pr.isMe) { item { EmptyState(t("Perfil privado.")) }; return@LazyColumn }
        pr.stats?.let { st ->
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("${st.movies.watched}", t("filmes"), Modifier.weight(1f))
                    StatTile("${st.episodes.watched}", t("episódios"), Modifier.weight(1f))
                    StatTile("${(st.movies.minutes + st.episodes.minutes) / 60}", t("horas"), Modifier.weight(1f))
                    StatTile("${st.ratings.total}", t("notas"), Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader(t("Viu por último")) }
        if (pr.history.isEmpty()) item { EmptyState(t("Nada no histórico.")) }
        items(pr.history, key = { "h-${it.entry.key}-${it.entry.at}" }) { FeedItem(it, showUser = false, nav = nav) }
        if (pr.ratings.isNotEmpty()) item {
            SectionHeader(t("Notas"), pr.ratings.size)
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(pr.ratings, key = { "r-${it.key}" }) { r ->
                    val k = coverKey("rate$slug", r.key)
                    Column(Modifier.width(100.dp).pressable(onClick = { nav.detail(r.key, k) })) {
                        Cover(TmdbImages.poster(r.posterPath), 100.dp, color = r.posterColor, sharedKey = k)
                        Spacer(Modifier.height(6.dp))
                        Text(r.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        ScoreView(r.raw, s.primaryScore, null, ScoreSize.Small)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    val p = AppTheme.palette
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Mono.style(18, FontWeight.SemiBold), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.textSecondary, maxLines = 1)
    }
}

// ---------------- Buscar pessoas ----------------

class UserSearchViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<TraktUser>?>(null)
    val error = MutableStateFlow<String?>(null)
    private var job: Job? = null

    fun onQuery(q: String) {
        query.value = q
        job?.cancel()
        if (q.trim().length < 2) { results.value = null; return }
        job = viewModelScope.launch {
            delay(500)
            try { results.value = c.social.search(q); error.value = null } catch (e: Exception) { error.value = e.toUserMessage() }
        }
    }
}

@Composable
fun UserSearchScreen(nav: Nav) {
    val vm = appViewModel { UserSearchViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Buscar pessoas"), onBack = nav::back)
        OutlinedTextField(
            query, vm::onQuery, singleLine = true, placeholder = { Text(t("Nome de usuário no Trakt")) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Default.Close, t("Limpar")) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp),
        )
        error?.let { MessageBar(it) }
        val list = results
        LazyColumn(Modifier.fillMaxSize()) {
            when {
                list == null -> item { EmptyState(t("Digite pelo menos duas letras.")) }
                list.isEmpty() -> item { EmptyState(t("Ninguém encontrado.")) }
                else -> items(list, key = { it.username }) { u ->
                    Box(Modifier.enterAnimation()) { UserRow(u) { nav.profile(u.ids.slug ?: u.username) } }
                }
            }
        }
    }
}
