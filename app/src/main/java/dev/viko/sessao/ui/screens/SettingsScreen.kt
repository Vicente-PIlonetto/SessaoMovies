package dev.viko.sessao.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.viko.sessao.BuildConfig
import dev.viko.sessao.data.api.TmdbImages
import dev.viko.sessao.data.settings.Accent
import dev.viko.sessao.data.settings.Settings
import dev.viko.sessao.data.settings.ThemeMode
import dev.viko.sessao.data.sync.Notifier
import dev.viko.sessao.data.sync.WorkScheduler
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.Provider
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.domain.score.ScoreSystem
import dev.viko.sessao.i18n.t
import dev.viko.sessao.ui.components.Dates
import dev.viko.sessao.ui.components.LinkRow
import dev.viko.sessao.ui.components.LocalSettings
import dev.viko.sessao.ui.components.MessageBar
import dev.viko.sessao.ui.components.Pill
import dev.viko.sessao.ui.components.ScoreSize
import dev.viko.sessao.ui.components.ScoreView
import dev.viko.sessao.ui.components.StatusIcon
import dev.viko.sessao.ui.components.TopBar
import dev.viko.sessao.ui.components.enterAnimation
import dev.viko.sessao.ui.nav.LocalContainer
import dev.viko.sessao.ui.nav.Nav
import dev.viko.sessao.ui.theme.AppTheme
import dev.viko.sessao.ui.theme.Mono
import dev.viko.sessao.ui.theme.statusColor
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class SettingsSection(val route: String, val title: String, val icon: ImageVector, val color: Long) {
    APPEARANCE("aparencia", t("Aparência"), Icons.Outlined.Palette, 0xFFF2A65E),
    SCORES("notas", t("Sistemas de nota"), Icons.Outlined.StarOutline, 0xFFF0CC4F),
    CATEGORIES("categorias", t("Ordem das categorias"), Icons.AutoMirrored.Outlined.ViewList, 0xFF6FD07A),
    SERVICES("servicos", t("Meus serviços de streaming"), Icons.Outlined.LiveTv, 0xFFEC6F82),
    NOTIFICATIONS("notificacoes", t("Notificações"), Icons.Outlined.Notifications, 0xFF4FB3F0),
    LETTERBOXD("letterboxd", "Letterboxd", Icons.Outlined.Sync, 0xFF4FD1BE),
    TMDB("tmdb", t("Chave do TMDB"), Icons.Outlined.Key, 0xFFAD96E6),
    BACKUP("backup", t("Backup"), Icons.Outlined.CloudSync, 0xFF8FA3B8),
    ABOUT("sobre", t("Sobre"), Icons.Outlined.Info, 0xFF8FA3B8),
}

private fun summary(k: SettingsSection, s: Settings): String = when (k) {
    SettingsSection.APPEARANCE -> listOf(s.theme.label, if (s.materialYou) "Material You" else s.accent.label).joinToString(" · ")
    SettingsSection.SCORES -> listOfNotNull(s.primaryScore.label, s.secondaryScore?.label).joinToString(" · ")
    SettingsSection.CATEGORIES -> t("Início e lista completa")
    SettingsSection.SERVICES -> if (s.myServices.isEmpty()) t("Nenhum marcado") else s.myServices.take(3).joinToString(", ") { it.name } + if (s.myServices.size > 3) "…" else ""
    SettingsSection.NOTIFICATIONS -> listOf(s.notifyEpisodes, s.notifyProviders).count { it }.let { if (it == 0) t("Desligadas") else t("{0} de 2 ligadas", it) }
    SettingsSection.LETTERBOXD -> if (s.letterboxdUser.isBlank()) t("Desligado") else "@" + s.letterboxdUser + if (s.letterboxdSync) "" else t(" · pausado")
    SettingsSection.TMDB -> if (s.tmdbKey.isNotBlank()) t("Usando a sua chave") else if (BuildConfig.TMDB_API_KEY.isNotBlank()) t("Usando a chave do app") else t("Sem chave")
    SettingsSection.BACKUP -> t("Exportar e importar")
    SettingsSection.ABOUT -> t("Versão {0}", BuildConfig.VERSION_NAME)
}

@Composable
fun SettingsScreen(nav: Nav, section: String? = null) {
    val s = LocalSettings.current
    val sec = SettingsSection.entries.firstOrNull { it.route == section }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar(sec?.title ?: t("Configurações"), onBack = nav::back)
        message?.let { MessageBar(it, t("OK")) { message = null } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            when (sec) {
                null -> {
                    var i = 0
                    Group(null) {
                        SettingsSection.entries.forEach { k -> CategoryRow(k, summary(k, s), Modifier.enterAnimation(i++)) { nav.settings(k.route) } }
                    }
                }
                SettingsSection.APPEARANCE -> AppearanceSection()
                SettingsSection.SCORES -> ScoresSection()
                SettingsSection.CATEGORIES -> CategoriesSection()
                SettingsSection.SERVICES -> ServicesSection { message = it }
                SettingsSection.NOTIFICATIONS -> NotificationsSection { message = it }
                SettingsSection.LETTERBOXD -> LetterboxdSection { message = it }
                SettingsSection.TMDB -> TmdbSection()
                SettingsSection.BACKUP -> BackupSection { message = it }
                SettingsSection.ABOUT -> AboutSection()
            }
        }
    }
}

// ---------------- Peças ----------------

/** Grupo de opções num cartão arredondado, com título pequeno acima. */
@Composable
fun Group(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val p = AppTheme.palette
    if (title != null) Text(title, style = MaterialTheme.typography.labelLarge, color = p.accent, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 6.dp))
    else Spacer(Modifier.height(10.dp))
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surfaceAlt).padding(vertical = 4.dp), content = content)
}

@Composable
private fun <T> Options(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) -> Pill(label, v == selected, { onSelect(v) }) }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, sub: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = if (enabled) p.text else p.textTertiary)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = switchColors())
    }
}

@Composable
private fun switchColors() = AppTheme.palette.let { p ->
    SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.background, uncheckedBorderColor = p.divider, uncheckedTrackColor = p.background, uncheckedThumbColor = p.textTertiary)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = AppTheme.palette.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun CategoryRow(k: SettingsSection, summary: String, modifier: Modifier, onClick: () -> Unit) {
    val p = AppTheme.palette
    val col = Color(k.color)
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(k.icon, null, tint = col, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(k.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.textTertiary)
    }
}

// ---------------- Seções ----------------

@Composable
private fun AppearanceSection() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val p = AppTheme.palette
    val scope = rememberCoroutineScope()
    Group(t("Tema")) {
        Options(ThemeMode.entries.map { it to it.label }, s.theme) { scope.launch { c.settings.setTheme(it) } }
        if (Build.VERSION.SDK_INT >= 31) SwitchRow(t("Material You (cores do papel de parede)"), s.materialYou) { v -> scope.launch { c.settings.setMaterialYou(v) } }
        SwitchRow(t("Preto puro no tema escuro"), s.oled) { v -> scope.launch { c.settings.setOled(v) } }
    }
    Group(t("Cor de destaque")) {
        if (s.materialYou && Build.VERSION.SDK_INT >= 31) Note(t("A cor vem do papel de parede."))
        else {
            val dark = p.background.luminance() < 0.5f
            FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Accent.entries.forEach { a ->
                    val sel = a == s.accent
                    val ring by androidx.compose.animation.core.animateDpAsState(if (sel) 2.dp else 0.dp, label = "ring")
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { scope.launch { c.settings.setAccent(a) } }) {
                        Box(Modifier.size(36.dp).border(ring, p.text, CircleShape).padding(5.dp).background(Color(if (dark) a.dark else a.light), CircleShape))
                        Text(a.label, style = MaterialTheme.typography.labelSmall, color = if (sel) p.text else p.textTertiary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Note(t("O ícone do app acompanha a cor. Ele muda quando você sai do app."))
        }
    }
}

@Composable
private fun ScoresSection() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    Group(t("Sistema de nota principal")) {
        Options(ScoreSystem.entries.map { it to it.label }, s.primaryScore) { scope.launch { c.settings.setPrimaryScore(it) } }
    }
    Group(t("Sistema de nota secundário")) {
        Options(listOf<Pair<ScoreSystem?, String>>(null to t("Desligado")) + ScoreSystem.entries.map { it to it.label }, s.secondaryScore) { scope.launch { c.settings.setSecondaryScore(it) } }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreView(89, s.primaryScore, s.secondaryScore, ScoreSize.Normal)
            Spacer(Modifier.width(24.dp))
            ScoreView(72, s.primaryScore, s.secondaryScore, ScoreSize.Normal)
            Spacer(Modifier.width(24.dp))
            ScoreView(41, s.primaryScore, s.secondaryScore, ScoreSize.Normal)
        }
    }
    Note(t("A nota fica guardada de 0 a 100; trocar de sistema só muda como ela aparece."))
}

/** Ordem das categorias: setas mudam a posição; o switch diz se vira aba no Início (pelo menos uma fica). */
@Composable
private fun CategoriesSection() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val p = AppTheme.palette
    val scope = rememberCoroutineScope()
    Note(t("Vale para o Início e para a lista completa. O switch mostra a categoria como aba no Início."))
    listOf(MediaType.TV, MediaType.MOVIE).forEach { type ->
        val order = s.order(type)
        val hidden = s.hidden(type)
        val shown = order.count { it !in hidden }
        Group(type.plural) {
            order.forEachIndexed { index, st ->
                val onHome = st !in hidden
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", style = Mono.style(12), color = p.textTertiary, modifier = Modifier.width(18.dp))
                    StatusIcon(st, statusColor(st), 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(st.label(type), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { c.settings.setOrder(type, order.toMutableList().apply { add(index - 1, removeAt(index)) }) } }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, t("Subir"), tint = if (index > 0) p.text else p.divider)
                    }
                    IconButton(onClick = { scope.launch { c.settings.setOrder(type, order.toMutableList().apply { add(index + 1, removeAt(index)) }) } }, enabled = index < order.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, t("Descer"), tint = if (index < order.lastIndex) p.text else p.divider)
                    }
                    Switch(
                        checked = onHome, enabled = !onHome || shown > 1, colors = switchColors(),
                        onCheckedChange = { v -> scope.launch { c.settings.setHidden(type, if (v) hidden - st else hidden + st) } },
                    )
                }
            }
            if (order != WatchStatus.defaultOrder(type) || hidden != Settings().hidden(type)) {
                LinkRow(t("Voltar ao padrão")) { scope.launch { c.settings.resetOrder(type) } }
            }
        }
    }
}

@Composable
private fun ServicesSection(onMessage: (String) -> Unit) {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val p = AppTheme.palette
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<Provider>?>(null) }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { runCatching { c.titles.providerList() }.onSuccess { all = it }.onFailure { onMessage(it.toUserMessage()) } }
    Note(t("Os serviços marcados aparecem primeiro em \"Onde assistir\", filtram o Descobrir e avisam quando algo da lista chega neles."))
    if (s.myServices.isNotEmpty()) Group(t("Marcados")) {
        FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.myServices.forEach { sv -> Pill(sv.name, true, { scope.launch { c.settings.setMyServices(s.myServices - sv) } }) }
        }
    }
    Group(t("Serviços no Brasil")) {
        OutlinedTextField(filter, { filter = it }, singleLine = true, placeholder = { Text(t("Filtrar")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(12.dp))
        val list = all
        if (list == null) Note(t("Carregando..."))
        else {
            val mine = s.myServiceIds
            list.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }.take(80).forEach { pr ->
                val on = pr.id in mine
                Row(
                    Modifier.fillMaxWidth().clickable { scope.launch { c.settings.setMyServices(if (on) s.myServices.filter { it.id != pr.id } else s.myServices + pr) } }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(TmdbImages.logo(pr.logoPath), null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(p.background))
                    Spacer(Modifier.width(12.dp))
                    Text(pr.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = if (on) p.accent else p.text)
                    Switch(checked = on, onCheckedChange = null, colors = switchColors())
                }
            }
        }
    }
}

@Composable
private fun NotificationsSection(onMessage: (String) -> Unit) {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted && action != null) scope.launch { action() }
        if (!granted) onMessage(t("Sem permissão de notificação. Libere nas configurações do Android."))
    }
    // A permissão só é pedida quando a primeira notificação é ligada.
    fun enable(v: Boolean, set: suspend (Boolean) -> Unit) {
        if (v && Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(ctx)) {
            pending = { set(true) }
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        scope.launch { set(v) }
    }
    Group(null) {
        SwitchRow(t("Episódio novo"), s.notifyEpisodes, t("Série que estou vendo, em dia ou revendo")) { enable(it) { v -> c.settings.setNotifyEpisodes(v) } }
        SwitchRow(t("Chegou num serviço meu"), s.notifyProviders, t("Item da lista que entrou num dos meus serviços")) { enable(it) { v -> c.settings.setNotifyProviders(v) } }
    }
    Note(t("A verificação roda uma vez por dia, com internet. Avisos do mesmo dia ficam agrupados."))
}

@Composable
private fun LetterboxdSection(onMessage: (String) -> Unit) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val s = LocalSettings.current
    val p = AppTheme.palette
    val scope = rememberCoroutineScope()
    var user by remember(s.letterboxdUser) { mutableStateOf(s.letterboxdUser) }
    var busy by remember { mutableStateOf<String?>(null) }
    var exportAll by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf<List<String>>(emptyList()) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = t("Importando...")
            runCatching { c.letterboxd.importZip(uri) { i, n -> busy = t("Importando {0} de {1}", i, n) } }
                .onSuccess { r ->
                    notFound = r.notFound
                    onMessage(if (r.notFound.isEmpty()) t("{0} filmes importados.", r.imported) else t("{0} filmes importados; {1} não encontrados no TMDB.", r.imported, r.notFound.size))
                    WorkScheduler.runNow(ctx)
                }
                .onFailure { onMessage(it.toUserMessage(t("Não foi possível importar o arquivo."))) }
            busy = null
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.letterboxd.export(uri, exportAll) }
                .onSuccess { n -> onMessage(if (n == 0) t("Nada novo para exportar.") else t("{0} filmes no arquivo. Envie o arquivo em letterboxd.com/import", n)) }
                .onFailure { onMessage(it.toUserMessage(t("Não foi possível salvar o arquivo."))) }
        }
    }

    Note(t("Só filmes. O app lê o feed público do seu perfil e os arquivos de exportação do Letterboxd; nada de login."))
    Group(t("Perfil")) {
        OutlinedTextField(
            user, { user = it.trim().trimStart('@') }, singleLine = true, placeholder = { Text(t("Nome de usuário")) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(12.dp),
            trailingIcon = { if (user != s.letterboxdUser) TextButton(onClick = { scope.launch { c.settings.setLetterboxdUser(user) } }) { Text(t("Salvar"), color = p.accent) } },
        )
        SwitchRow(t("Sincronizar"), s.letterboxdSync, t("Os filmes registrados lá entram aqui como vistos, uma vez por dia"), enabled = s.letterboxdUser.isNotBlank()) { v -> scope.launch { c.settings.setLetterboxdSync(v) } }
        Text(
            if (s.letterboxdLastSync > 0) t("Última sincronização: {0}", Dates.full(Dates.of(s.letterboxdLastSync))) else t("Ainda não sincronizado."),
            style = Mono.style(12), color = p.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LinkRow(t("Sincronizar agora"), icon = Icons.Outlined.Sync) {
            if (busy == null && s.letterboxdUser.isNotBlank()) scope.launch {
                busy = t("Sincronizando...")
                runCatching { c.letterboxd.syncFeed() }
                    .onSuccess { n -> onMessage(if (n == 0) t("Nada novo no Letterboxd.") else t("{0} entradas novas do Letterboxd.", n)) }
                    .onFailure { onMessage(it.toUserMessage()) }
                busy = null
            }
        }
    }
    busy?.let { Note(it) }
    Group(t("Histórico completo")) {
        Note(t("O feed só traz as entradas recentes. Para o resto, exporte seus dados no Letterboxd (Configurações > Dados > Exportar) e importe o .zip aqui."))
        LinkRow(t("Importar .zip do Letterboxd"), icon = Icons.Outlined.Download) { if (busy == null) importer.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
        if (notFound.isNotEmpty()) {
            Note(t("Não encontrados no TMDB (adicione pela busca):"))
            Text(notFound.joinToString("\n"), style = Mono.style(12), color = p.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
    Group(t("Exportar para o Letterboxd")) {
        SwitchRow(t("Exportar tudo"), exportAll, if (exportAll) t("Todos os filmes vistos ou com nota") else t("Só o que mudou desde a última exportação")) { exportAll = it }
        LinkRow(t("Gerar CSV"), icon = Icons.Outlined.Upload) { exporter.launch("sessao-letterboxd-${LocalDate.now()}.csv") }
        if (s.letterboxdLastExport > 0) Text(t("Última exportação: {0}", Dates.full(Dates.of(s.letterboxdLastExport))), style = Mono.style(12), color = p.textSecondary, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun TmdbSection() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val p = AppTheme.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember(s.tmdbKey) { mutableStateOf(s.tmdbKey) }
    Note(
        if (BuildConfig.TMDB_API_KEY.isNotBlank()) t("O app já vem com uma chave. Use a sua se quiser (por exemplo, se a do app parar de funcionar).")
        else t("Este APK não tem chave embutida. Crie uma de graça em themoviedb.org e cole aqui.")
    )
    Group(null) {
        OutlinedTextField(
            key, { key = it.trim() }, singleLine = true, placeholder = { Text(t("Chave da API (v3) ou token de leitura")) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp),
        )
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { scope.launch { c.settings.setTmdbKey(key) } }, enabled = key != s.tmdbKey) { Text(t("Salvar"), color = p.accent) }
            if (s.tmdbKey.isNotBlank()) TextButton(onClick = { key = ""; scope.launch { c.settings.setTmdbKey("") } }) { Text(t("Usar a chave do app"), color = p.textSecondary) }
        }
        LinkRow(t("Criar uma chave no TMDB")) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/settings/api")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

@Composable
private fun BackupSection(onMessage: (String) -> Unit) {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { onMessage(runCatching { c.backup.export(uri) }.fold({ t("Backup salvo.") }, { t("Não foi possível salvar o backup.") })) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.backup.import(uri) }
                .onSuccess { n -> onMessage(t("Backup restaurado: {0} itens. Os dados do TMDB vão ser baixados de novo.", n)); WorkScheduler.runNow(ctx) }
                .onFailure { onMessage(t("Arquivo inválido.")) }
        }
    }
    Note(t("Lista, episódios marcados, notas, anotações, serviços e configurações do Letterboxd. A chave do TMDB fica de fora."))
    Group(null) {
        LinkRow(t("Exportar"), icon = Icons.Outlined.Upload) { exporter.launch("sessao-backup-${LocalDate.now()}.json") }
        LinkRow(t("Importar"), icon = Icons.Outlined.Download) { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
    }
}

@Composable
private fun AboutSection() {
    val p = AppTheme.palette
    val ctx = LocalContext.current
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Group(null) {
        Column(Modifier.padding(16.dp)) {
            Text(t("Sessão"), style = MaterialTheme.typography.titleLarge)
            Text(t("Versão {0}", BuildConfig.VERSION_NAME), style = Mono.style(13), color = p.textSecondary)
            Spacer(Modifier.height(8.dp))
            Text(t("Watchlist de filmes e séries: o que estou vendo, em que episódio parei e onde dá para assistir no Brasil."), style = MaterialTheme.typography.bodyMedium)
        }
        LinkRow(t("Código-fonte")) { open(BuildConfig.REPO_URL) }
    }
    Group(t("Créditos")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Dados de filmes e séries: TMDB. Este app usa a API do TMDB, mas não é endossado nem certificado pelo TMDB."), style = MaterialTheme.typography.bodySmall)
            Text(t("Dados de streaming: JustWatch."), style = MaterialTheme.typography.bodySmall)
            Text(t("Letterboxd: só o feed público e os arquivos de exportação. O app não é afiliado ao Letterboxd."), style = MaterialTheme.typography.bodySmall)
            Text(t("Fontes IBM Plex Sans e IBM Plex Mono, sob a SIL Open Font License."), style = MaterialTheme.typography.bodySmall)
            Text(t("Licença GPL-3.0."), style = MaterialTheme.typography.bodySmall)
        }
        LinkRow("themoviedb.org") { open("https://www.themoviedb.org") }
        LinkRow("justwatch.com") { open("https://www.justwatch.com/br") }
    }
}
