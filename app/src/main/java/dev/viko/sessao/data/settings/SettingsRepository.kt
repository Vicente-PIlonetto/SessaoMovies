package dev.viko.sessao.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.Provider
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.domain.score.ScoreSystem
import dev.viko.sessao.i18n.t
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class ThemeMode(val label: String) { SYSTEM(t("Sistema")), LIGHT(t("Claro")), DARK(t("Escuro")), OLED(t("Preto (OLED)")) }

/** Cor de destaque: (claro, escuro). */
@Serializable
enum class Accent(val label: String, val light: Long, val dark: Long) {
    AMBAR(t("Âmbar"), 0xFF94650F, 0xFFE2B252),
    TERRACOTA(t("Terracota"), 0xFFA4502F, 0xFFD98A66),
    AZUL(t("Azul"), 0xFF2F6CA8, 0xFF6FAEE8),
    VERDE(t("Verde"), 0xFF3B7A4C, 0xFF7CC08E),
    ROSA(t("Rosa"), 0xFFAD4470, 0xFFE58AAE),
    ROXO(t("Roxo"), 0xFF6A4CA6, 0xFFAD96E6),
    VERMELHO(t("Vermelho"), 0xFFB23A3A, 0xFFE88080),
}

/** Ordenação da grade do Início e da lista completa. */
@Serializable
enum class ListSort(val label: String) {
    NEW_FIRST(t("Episódio novo primeiro")),
    RECENT(t("Atividade recente")),
    ADDED(t("Adicionado")),
    TITLE(t("Título")),
    SCORE(t("Nota")),
    YEAR(t("Ano")),
}

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.AMBAR,
    val materialYou: Boolean = false,
    val oled: Boolean = false,
    val primaryScore: ScoreSystem = ScoreSystem.LETTERS,
    val secondaryScore: ScoreSystem? = null,
    val orderTv: List<WatchStatus> = WatchStatus.defaultOrder(MediaType.TV),
    val orderMovie: List<WatchStatus> = WatchStatus.defaultOrder(MediaType.MOVIE),
    val hiddenTv: Set<WatchStatus> = setOf(WatchStatus.COMPLETED, WatchStatus.DROPPED),
    val hiddenMovie: Set<WatchStatus> = setOf(WatchStatus.DROPPED),
    val homeSort: ListSort = ListSort.NEW_FIRST,
    val myServices: List<Provider> = emptyList(),
    val notifyEpisodes: Boolean = false,
    val notifyProviders: Boolean = false,
    val letterboxdUser: String = "",
    val letterboxdSync: Boolean = false,
    val letterboxdLastSync: Long = 0,
    val letterboxdLastExport: Long = 0,
    val tmdbKey: String = "",
    val firstRunDone: Boolean = false,
    val scheduleShowAll: Boolean = false,
) {
    val myServiceIds: Set<Int> get() = myServices.map { it.id }.toSet()

    fun order(type: MediaType): List<WatchStatus> = normalize(if (type == MediaType.TV) orderTv else orderMovie, type)
    fun hidden(type: MediaType): Set<WatchStatus> = if (type == MediaType.TV) hiddenTv else hiddenMovie

    /** Abas do Início: a ordem escolhida sem as escondidas (pelo menos uma). */
    fun homeTabs(type: MediaType): List<WatchStatus> = order(type).filter { it !in hidden(type) }.ifEmpty { order(type).take(1) }

    companion object {
        /** Status que faltarem (ou não existirem no tipo) vão para o fim, na ordem padrão. */
        fun normalize(order: List<WatchStatus>, type: MediaType): List<WatchStatus> {
            val valid = WatchStatus.forType(type)
            return (order.filter { it in valid } + WatchStatus.defaultOrder(type)).distinct()
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val materialYou = booleanPreferencesKey("material_you")
        val oled = booleanPreferencesKey("oled")
        val primary = stringPreferencesKey("primary_score")
        val secondary = stringPreferencesKey("secondary_score")
        val orderTv = stringPreferencesKey("order_tv")
        val orderMovie = stringPreferencesKey("order_movie")
        val hiddenTv = stringSetPreferencesKey("hidden_tv")
        val hiddenMovie = stringSetPreferencesKey("hidden_movie")
        val homeSort = stringPreferencesKey("home_sort")
        val services = stringPreferencesKey("my_services")
        val nEpisodes = booleanPreferencesKey("notify_episodes")
        val nProviders = booleanPreferencesKey("notify_providers")
        val lbUser = stringPreferencesKey("letterboxd_user")
        val lbSync = booleanPreferencesKey("letterboxd_sync")
        val lbLastSync = longPreferencesKey("letterboxd_last_sync")
        val lbLastExport = longPreferencesKey("letterboxd_last_export")
        val tmdbKey = stringPreferencesKey("tmdb_key")
        val firstRun = booleanPreferencesKey("first_run_done")
        val scheduleAll = booleanPreferencesKey("schedule_show_all")
        val lastRefresh = longPreferencesKey("last_refresh")
        val notifiedDay = stringPreferencesKey("notified_day")
        val profileStyle = stringPreferencesKey("profile_style")
    }

    private fun statuses(raw: String?): List<WatchStatus>? = raw?.split(',')?.mapNotNull { WatchStatus.parse(it) }?.takeIf { it.isNotEmpty() }
    private fun statusSet(raw: Set<String>?): Set<WatchStatus>? = raw?.mapNotNull { WatchStatus.parse(it) }?.toSet()

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            accent = p[K.accent]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: d.accent,
            materialYou = p[K.materialYou] ?: false,
            oled = p[K.oled] ?: false,
            primaryScore = ScoreSystem.parse(p[K.primary]) ?: d.primaryScore,
            secondaryScore = ScoreSystem.parse(p[K.secondary]),
            orderTv = statuses(p[K.orderTv]) ?: d.orderTv,
            orderMovie = statuses(p[K.orderMovie]) ?: d.orderMovie,
            hiddenTv = statusSet(p[K.hiddenTv]) ?: d.hiddenTv,
            hiddenMovie = statusSet(p[K.hiddenMovie]) ?: d.hiddenMovie,
            homeSort = p[K.homeSort]?.let { runCatching { ListSort.valueOf(it) }.getOrNull() } ?: d.homeSort,
            myServices = p[K.services]?.let { runCatching { json.decodeFromString(ListSerializer(Provider.serializer()), it) }.getOrNull() } ?: emptyList(),
            notifyEpisodes = p[K.nEpisodes] ?: false,
            notifyProviders = p[K.nProviders] ?: false,
            letterboxdUser = p[K.lbUser].orEmpty(),
            letterboxdSync = p[K.lbSync] ?: false,
            letterboxdLastSync = p[K.lbLastSync] ?: 0,
            letterboxdLastExport = p[K.lbLastExport] ?: 0,
            tmdbKey = p[K.tmdbKey].orEmpty(),
            firstRunDone = p[K.firstRun] ?: false,
            scheduleShowAll = p[K.scheduleAll] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    val profileStyle: Flow<ProfileStyle> = context.dataStore.data.map { p ->
        p[K.profileStyle]?.let { runCatching { json.decodeFromString(ProfileStyle.serializer(), it) }.getOrNull() } ?: ProfileStyle()
    }

    suspend fun setProfileStyle(v: ProfileStyle) = context.dataStore.edit { it[K.profileStyle] = json.encodeToString(ProfileStyle.serializer(), v) }
    val lastRefresh: Flow<Long> = context.dataStore.data.map { it[K.lastRefresh] ?: 0L }

    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[K.theme] = v.name }
    suspend fun setAccent(v: Accent) = context.dataStore.edit { it[K.accent] = v.name }
    suspend fun setMaterialYou(v: Boolean) = context.dataStore.edit { it[K.materialYou] = v }
    suspend fun setOled(v: Boolean) = context.dataStore.edit { it[K.oled] = v }
    suspend fun setPrimaryScore(v: ScoreSystem) = context.dataStore.edit { it[K.primary] = v.name }
    suspend fun setSecondaryScore(v: ScoreSystem?) = context.dataStore.edit { if (v == null) it.remove(K.secondary) else it[K.secondary] = v.name }
    suspend fun setOrder(type: MediaType, v: List<WatchStatus>) = context.dataStore.edit {
        it[if (type == MediaType.TV) K.orderTv else K.orderMovie] = Settings.normalize(v, type).joinToString(",") { s -> s.name }
    }
    suspend fun setHidden(type: MediaType, v: Set<WatchStatus>) = context.dataStore.edit {
        it[if (type == MediaType.TV) K.hiddenTv else K.hiddenMovie] = v.map { s -> s.name }.toSet()
    }
    suspend fun resetOrder(type: MediaType) = context.dataStore.edit {
        it.remove(if (type == MediaType.TV) K.orderTv else K.orderMovie)
        it.remove(if (type == MediaType.TV) K.hiddenTv else K.hiddenMovie)
    }
    suspend fun setHomeSort(v: ListSort) = context.dataStore.edit { it[K.homeSort] = v.name }
    suspend fun setMyServices(v: List<Provider>) = context.dataStore.edit {
        it[K.services] = json.encodeToString(ListSerializer(Provider.serializer()), v.distinctBy { p -> p.id })
    }
    suspend fun setNotifyEpisodes(v: Boolean) = context.dataStore.edit { it[K.nEpisodes] = v }
    suspend fun setNotifyProviders(v: Boolean) = context.dataStore.edit { it[K.nProviders] = v }
    suspend fun setLetterboxdUser(v: String) = context.dataStore.edit { it[K.lbUser] = v.trim().trimStart('@') }
    suspend fun setLetterboxdSync(v: Boolean) = context.dataStore.edit { it[K.lbSync] = v }
    suspend fun setLetterboxdLastSync(v: Long) = context.dataStore.edit { it[K.lbLastSync] = v }
    suspend fun setLetterboxdLastExport(v: Long) = context.dataStore.edit { it[K.lbLastExport] = v }
    suspend fun setTmdbKey(v: String) = context.dataStore.edit { it[K.tmdbKey] = v.trim() }
    suspend fun setFirstRunDone() = context.dataStore.edit { it[K.firstRun] = true }
    suspend fun setScheduleShowAll(v: Boolean) = context.dataStore.edit { it[K.scheduleAll] = v }
    suspend fun setLastRefresh(v: Long) = context.dataStore.edit { it[K.lastRefresh] = v }

    /** Dia (aaaa-mm-dd) das últimas notificações, para agrupar as do mesmo dia. */
    suspend fun notifiedDay(): String? = context.dataStore.data.first()[K.notifiedDay]
    suspend fun setNotifiedDay(v: String) = context.dataStore.edit { it[K.notifiedDay] = v }

    /** Restaura as configurações de um backup. */
    suspend fun restore(s: Settings) = context.dataStore.edit {
        it[K.theme] = s.theme.name
        it[K.accent] = s.accent.name
        it[K.materialYou] = s.materialYou
        it[K.oled] = s.oled
        it[K.primary] = s.primaryScore.name
        if (s.secondaryScore == null) it.remove(K.secondary) else it[K.secondary] = s.secondaryScore.name
        it[K.orderTv] = s.orderTv.joinToString(",") { x -> x.name }
        it[K.orderMovie] = s.orderMovie.joinToString(",") { x -> x.name }
        it[K.hiddenTv] = s.hiddenTv.map { x -> x.name }.toSet()
        it[K.hiddenMovie] = s.hiddenMovie.map { x -> x.name }.toSet()
        it[K.homeSort] = s.homeSort.name
        it[K.services] = json.encodeToString(ListSerializer(Provider.serializer()), s.myServices)
        it[K.nEpisodes] = s.notifyEpisodes
        it[K.nProviders] = s.notifyProviders
        it[K.lbUser] = s.letterboxdUser
        it[K.lbSync] = s.letterboxdSync
        it[K.lbLastSync] = s.letterboxdLastSync
        it[K.lbLastExport] = s.letterboxdLastExport
        it[K.firstRun] = true
        it[K.scheduleAll] = s.scheduleShowAll
        // A chave do TMDB não vai no backup.
    }
}
