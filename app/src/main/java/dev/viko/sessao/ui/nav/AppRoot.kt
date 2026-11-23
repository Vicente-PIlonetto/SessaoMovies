package dev.viko.sessao.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.viko.sessao.AppContainer
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.Title
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.LocalNavAnimScope
import dev.viko.sessao.ui.components.LocalQuickStatus
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.LocalSharedScope
import dev.viko.sessao.ui.components.LocalStatusMap
import dev.viko.sessao.ui.components.QuickStatusSheet
import dev.viko.sessao.ui.components.ScorePickerSheet
import dev.viko.sessao.ui.components.ThinDivider
import dev.viko.sessao.ui.screens.DetailScreen
import dev.viko.sessao.ui.screens.DiscoverScreen
import dev.viko.sessao.ui.screens.HomeScreen
import dev.viko.sessao.ui.screens.ListScreen
import dev.viko.sessao.ui.screens.NotificationsScreen
import dev.viko.sessao.ui.screens.ScheduleScreen
import dev.viko.sessao.ui.screens.SearchScreen
import dev.viko.sessao.ui.screens.SettingsScreen
import dev.viko.sessao.ui.screens.YouScreen
import dev.viko.sessao.ui.theme.AppTheme
import kotlinx.coroutines.launch

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("sem container") }

/** Navegação usada pelas telas. */
class Nav(private val c: NavHostController) {
    fun back() { c.popBackStack() }
    fun detail(key: TitleKey, coverKey: String? = null) = c.navigate("detail/${key.type.path}/${key.id}?k=${Uri.encode(coverKey.orEmpty())}")
    fun list(type: MediaType) = c.navigate("list/${type.path}")
    fun search() = c.navigate("search")
    fun notifications() = c.navigate("notifications")
    fun settings(section: String? = null) = c.navigate(if (section == null) "settings" else "settings/$section")
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", t("Início"), Icons.Outlined.Home, Icons.Filled.Home),
    Tab("schedule", t("Agenda"), Icons.Outlined.CalendarToday, Icons.Filled.CalendarToday),
    Tab("discover", t("Descobrir"), Icons.Outlined.Explore, Icons.Filled.Explore),
    Tab("you", t("Você"), Icons.Outlined.Person, Icons.Filled.Person),
)
private val tabRoutes = tabs.map { it.route }.toSet()
private fun isTab(e: NavBackStackEntry) = e.destination.route in tabRoutes
private const val TAB_MS = 240

// Entre abas, só um fade curto; para dentro, desliza com mola; voltar, o contrário.
private val slideSpring = spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = IntOffset(1, 1))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (isTab(initialState) && isTab(targetState)) slideInVertically(tween(TAB_MS)) { 0 }
    else slideInHorizontally(slideSpring) { it / 3 } + fadeIn(tween(200))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (isTab(initialState) && isTab(targetState)) slideOutVertically(tween(TAB_MS)) { 0 }
    else slideOutHorizontally(slideSpring) { -it / 8 } + fadeOut(tween(180))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(slideSpring) { -it / 8 } + fadeIn(tween(200))
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(slideSpring) { it / 3 } + fadeOut(tween(160))

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppRoot(openTitle: String?, onOpened: () -> Unit) {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val back by controller.currentBackStackEntryAsState()
    val route = back?.destination?.route
    val p = AppTheme.palette
    val scope = rememberCoroutineScope()
    val statusMap by c.lists.statusMap.collectAsStateWithLifecycle(initialValue = emptyMap())
    var quick by remember { mutableStateOf<Title?>(null) }
    var scoreFor by remember { mutableStateOf<Title?>(null) }

    LaunchedEffect(openTitle) {
        val key = openTitle?.let { TitleKey.parse(it) }
        if (key != null) { nav.detail(key); onOpened() }
    }

    CompositionLocalProvider(
        LocalStatusMap provides statusMap,
        LocalQuickStatus provides { t: Title -> quick = t },
    ) {
        // A barra de baixo fica por cima do conteúdo; as abas reservam o espaço dela (veja screen()).
        Box(Modifier.fillMaxSize().background(p.background)) {
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedScope provides this) {
                    NavHost(
                        controller, startDestination = "home",
                        enterTransition = { enter() }, exitTransition = { exit() },
                        popEnterTransition = { popEnter() }, popExitTransition = { popExit() },
                    ) { graph(nav) }
                }
            }
            AnimatedVisibility(
                visible = route == null || route in tabRoutes,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120)),
            ) {
                Column {
                    ThinDivider()
                    NavigationBar(containerColor = p.background, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    controller.navigate(tab.route) {
                                        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    // O ícone pula ao ser escolhido.
                                    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
                                    LaunchedEffect(selected) {
                                        if (selected) { scale.snapTo(0.7f); scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f)) }
                                    }
                                    androidx.compose.animation.Crossfade(selected, label = "tab", modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) { on ->
                                        Icon(if (on) tab.selectedIcon else tab.icon, null, tint = if (on) p.accent else p.textTertiary)
                                    }
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = p.accent, selectedTextColor = p.accent,
                                    unselectedIconColor = p.textTertiary, unselectedTextColor = p.textTertiary,
                                    indicatorColor = p.accent.copy(alpha = 0.16f),
                                ),
                            )
                        }
                    }
                }
            }
        }

        quick?.let { title ->
            QuickStatusSheet(
                title = title, current = statusMap[title.key],
                onDismiss = { quick = null },
                onPick = { st ->
                    quick = null
                    scope.launch {
                        c.lists.add(title, st)
                        // Adicionado pela busca ou pelo Descobrir: baixa o detalhe para a lista abrir offline.
                        if (title.updatedAt == 0L) runCatching { c.titles.refresh(title.key) }.onSuccess { c.lists.recompute(title.key) }
                    }
                },
                onRemove = { quick = null; scope.launch { c.lists.remove(title.key) } },
                onOpen = { quick = null; nav.detail(title.key) },
                onScore = { quick = null; scoreFor = title },
            )
        }
        scoreFor?.let { title ->
            val entry by remember(title.key) { c.lists.item(title.key) }.collectAsStateWithLifecycle(initialValue = null)
            val e = entry?.entry
            if (e != null) ScorePickerSheet(
                initialRaw = e.scoreRaw, system = s.primaryScore, subtitle = title.name,
                onDismiss = { scoreFor = null },
                onSave = { picked -> scoreFor = null; scope.launch { c.lists.setScore(title.key, picked.raw) } },
            )
        }
        if (!s.firstRunDone) FirstRunSheet(
            onServices = { scope.launch { c.settings.setFirstRunDone() }; nav.settings("servicos") },
            onLetterboxd = { user -> scope.launch { c.settings.setLetterboxdUser(user); c.settings.setLetterboxdSync(true); c.settings.setFirstRunDone() } },
            onSkip = { scope.launch { c.settings.setFirstRunDone() } },
        )
    }
}

/** Primeira abertura: um aviso curto, sem slides. Dá para pular. */
@Composable
private fun FirstRunSheet(onServices: () -> Unit, onLetterboxd: (String) -> Unit, onSkip: () -> Unit) {
    val p = AppTheme.palette
    var user by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onSkip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = p.background,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Text(t("Antes de começar"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                t("Marque os serviços de streaming que você assina: eles aparecem primeiro em \"Onde assistir\" e dá para receber aviso quando algo da lista chegar neles."),
                style = MaterialTheme.typography.bodyMedium, color = p.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onServices, shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.background),
            ) { Text(t("Escolher serviços")) }
            Spacer(Modifier.height(20.dp))
            Text(t("Usa o Letterboxd? Com o nome de usuário, os filmes que você registrar lá entram aqui como vistos."), style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(user, { user = it.trim() }, singleLine = true, placeholder = { Text(t("Usuário do Letterboxd")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSkip) { Text(t("Pular"), color = p.textSecondary) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onLetterboxd(user) }, enabled = user.isNotBlank()) { Text(t("Salvar"), color = p.accent) }
            }
        }
    }
}

/** Cada destino recebe o AnimatedVisibilityScope para os pôsteres compartilhados. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimScope provides this) {
        if (route in tabRoutes) {
            // Troca de aba: a nova sobe um pouco e aparece, com a opacidade aplicada direto no desenho.
            val v by transition.animateFloat(
                transitionSpec = { tween(TAB_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing) }, label = "tab",
            ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
            // Espaço fixo da barra de baixo (80dp + barra de navegação do sistema).
            Box(
                Modifier.fillMaxSize().padding(bottom = 81.dp).navigationBarsPadding().graphicsLayer {
                    alpha = v
                    translationY = (1f - v) * 18.dp.toPx()
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                },
            ) { content(entry) }
        } else content(entry)
    }
}

private fun NavGraphBuilder.graph(nav: Nav) {
    screen("home") { HomeScreen(nav) }
    screen("schedule") { ScheduleScreen(nav) }
    screen("discover") { DiscoverScreen(nav) }
    screen("you") { YouScreen(nav) }
    screen("search") { SearchScreen(nav) }
    screen("notifications") { NotificationsScreen(nav) }
    screen("settings") { SettingsScreen(nav) }
    screen("settings/{section}") { SettingsScreen(nav, it.arguments?.getString("section")) }
    screen("list/{type}") { ListScreen(nav, MediaType.fromPath(it.arguments?.getString("type")) ?: MediaType.TV) }
    screen(
        "detail/{type}/{id}?k={k}",
        listOf(
            navArgument("type") { type = NavType.StringType },
            navArgument("id") { type = NavType.IntType },
            navArgument("k") { type = NavType.StringType; defaultValue = "" },
        ),
    ) {
        val type = MediaType.fromPath(it.arguments?.getString("type")) ?: MediaType.MOVIE
        DetailScreen(nav, TitleKey(type, it.arguments!!.getInt("id")), it.arguments?.getString("k")?.takeIf { k -> k.isNotBlank() })
    }
}
