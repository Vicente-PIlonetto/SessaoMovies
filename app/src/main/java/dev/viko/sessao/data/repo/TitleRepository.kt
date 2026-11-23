package dev.viko.sessao.data.repo

import android.content.Context
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.viko.sessao.data.api.DetailExtras
import dev.viko.sessao.data.api.Tmdb
import dev.viko.sessao.data.api.TmdbImages
import dev.viko.sessao.data.api.TitleSummary
import dev.viko.sessao.data.api.toAired
import dev.viko.sessao.data.api.toCast
import dev.viko.sessao.data.api.toInfo
import dev.viko.sessao.data.api.toProviders
import dev.viko.sessao.data.api.toSummary
import dev.viko.sessao.data.api.toTitle
import dev.viko.sessao.data.db.AppDatabase
import dev.viko.sessao.domain.EpisodeInfo
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.Provider
import dev.viko.sessao.domain.Title
import dev.viko.sessao.domain.TitleKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** Dados do TMDB: detalhe, temporadas, provedores, Descobrir e busca. */
class TitleRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val tmdb: Tmdb,
    private val images: ImageLoader,
) {
    private val extrasCache = ConcurrentHashMap<TitleKey, DetailExtras>()

    fun observe(key: TitleKey): Flow<Title?> = db.titles().observe(key.type, key.id)
    fun episodes(showId: Int): Flow<List<EpisodeInfo>> = db.episodes().observeShow(showId)
    fun extras(key: TitleKey): DetailExtras? = extrasCache[key]

    /**
     * Baixa o detalhe e guarda. Séries também baixam as temporadas com episódio anunciado ou recente
     * (para a Agenda e o "+1"). Sinopse vazia em português vira a em inglês.
     */
    suspend fun refresh(key: TitleKey, withSeasons: Set<Int> = emptySet()): Title {
        val old = db.titles().get(key.type, key.id)
        val t = now()
        val title = when (key.type) {
            MediaType.MOVIE -> {
                val d = tmdb.call { movie(key.id) }
                val en = if (d.overview.isNullOrBlank()) runCatching { tmdb.call { movieIn(key.id, "en-US") }.overview }.getOrNull() else null
                extrasCache[key] = DetailExtras(d.credits.toCast(), d.similar?.results.orEmpty().mapNotNull { it.toSummary(MediaType.MOVIE) })
                d.toTitle(old, t, en)
            }
            MediaType.TV -> {
                val d = tmdb.call { tv(key.id) }
                val en = if (d.overview.isNullOrBlank()) runCatching { tmdb.call { tvIn(key.id, "en-US") }.overview }.getOrNull() else null
                extrasCache[key] = DetailExtras(d.credits.toCast(), d.similar?.results.orEmpty().mapNotNull { it.toSummary(MediaType.TV) })
                d.toTitle(old, t, en)
            }
        }
        db.titles().upsert(title)
        if (key.type == MediaType.TV) {
            val seasons = (withSeasons + listOfNotNull(title.lastAired?.season, title.nextEpisode?.season)).filter { s -> title.seasons.any { it.number == s } }
            seasons.forEach { runCatching { loadSeason(key.id, it) } }
        }
        if (title.posterColor == null) extractColor(title)
        return title
    }

    suspend fun loadSeason(showId: Int, season: Int) {
        val s = tmdb.call { season(showId, season) }
        db.episodes().upsert(s.episodes.map { it.toInfo(showId) })
    }

    /** Só os provedores (atualização diária). Retorna os novos e os antigos. */
    suspend fun refreshProviders(key: TitleKey): Pair<dev.viko.sessao.domain.WatchProviders?, dev.viko.sessao.domain.WatchProviders?> {
        val old = db.titles().get(key.type, key.id) ?: return null to null
        val fresh = tmdb.call { providers(key.type.path, key.id) }.toProviders()
        db.titles().upsert(old.copy(providers = fresh ?: old.providers, providersUpdatedAt = now()))
        return old.providers.takeIf { old.providersUpdatedAt > 0 } to fresh
    }

    /** Cor dominante do pôster com a Palette; o TMDB não manda essa cor. */
    private suspend fun extractColor(title: Title) {
        val url = TmdbImages.poster(title.posterPath, "w185") ?: return
        val color = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(120, 180).build()
                val res = images.execute(req) as? SuccessResult ?: return@runCatching null
                val p = Palette.from(res.image.toBitmap()).maximumColorCount(16).generate()
                (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch ?: p.dominantSwatch)?.rgb
            }.getOrNull()
        } ?: return
        db.titles().setColor(title.type, title.id, color)
    }

    // ---------- Busca e Descobrir (precisam de internet) ----------

    suspend fun search(query: String, page: Int = 1): List<TitleSummary> =
        tmdb.call { searchMulti(query, page) }.results.mapNotNull { it.toSummary() }

    suspend fun searchMovie(name: String, year: Int?): TitleSummary? =
        tmdb.call { searchMovie(name, year) }.results.firstNotNullOfOrNull { it.toSummary(MediaType.MOVIE) }
            ?: if (year != null) tmdb.call { searchMovie(name, null) }.results.firstNotNullOfOrNull { it.toSummary(MediaType.MOVIE) } else null

    suspend fun trending(): List<TitleSummary> = tmdb.call { trending() }.results.mapNotNull { it.toSummary() }

    suspend fun popular(type: MediaType): List<TitleSummary> = tmdb.call { popular(type.path) }.results.mapNotNull { it.toSummary(type) }

    suspend fun nowPlaying(): List<TitleSummary> = tmdb.call { nowPlaying() }.results.mapNotNull { it.toSummary(MediaType.MOVIE) }

    /** Filmes que chegaram (ou chegam) ao streaming no Brasil: lançamento digital recente. */
    suspend fun arrivingStreaming(): List<TitleSummary> {
        val today = LocalDate.now()
        return tmdb.call {
            discover("movie", 1, mapOf(
                "with_release_type" to "4",
                "release_date.gte" to today.minusDays(45).toString(),
                "release_date.lte" to today.plusDays(14).toString(),
                "sort_by" to "popularity.desc",
                "with_watch_monetization_types" to "flatrate|free|ads|rent|buy",
            ))
        }.results.mapNotNull { it.toSummary(MediaType.MOVIE) }
    }

    /** Descobrir com filtros: gênero e serviços (ids separados por "|"). */
    suspend fun discover(type: MediaType, genre: Int?, providers: Set<Int>, page: Int = 1): List<TitleSummary> {
        val f = buildMap {
            put("sort_by", "popularity.desc")
            put("vote_count.gte", "30")
            genre?.let { put("with_genres", it.toString()) }
            if (providers.isNotEmpty()) {
                put("with_watch_providers", providers.joinToString("|"))
                put("with_watch_monetization_types", "flatrate|free|ads")
            }
        }
        return tmdb.call { discover(type.path, page, f) }.results.mapNotNull { it.toSummary(type) }
    }

    /** Séries com episódio na semana (para "Tudo em exibição" da Agenda). */
    suspend fun airingBetween(from: LocalDate, to: LocalDate): List<TitleSummary> =
        tmdb.call {
            discover("tv", 1, mapOf(
                "air_date.gte" to from.toString(), "air_date.lte" to to.toString(),
                "sort_by" to "popularity.desc", "with_original_language" to "en|pt|es|ko|ja|fr|de",
            ))
        }.results.mapNotNull { it.toSummary(MediaType.TV) }

    /**
     * Episódios da semana de séries em exibição que não estão na minha lista.
     * Pega as mais populares e lê a data do próximo e do último episódio de cada uma.
     */
    suspend fun airingEpisodes(from: LocalDate, to: LocalDate, skip: Set<Int>, limit: Int = 20): List<Pair<TitleSummary, dev.viko.sessao.domain.AiredEpisode>> {
        val shows = airingBetween(from, to).filter { it.key.id !in skip }.take(limit)
        return shows.flatMap { s ->
            val d = runCatching { tmdb.call { tvBasic(s.key.id) } }.getOrNull() ?: return@flatMap emptyList()
            listOfNotNull(d.lastEpisode, d.nextEpisode).map { it.toAired() }
                .filter { e -> e.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it in from..to } == true }
                .map { s to it }
        }
    }

    private val genreCache = ConcurrentHashMap<MediaType, List<Pair<Int, String>>>()
    suspend fun genres(type: MediaType): List<Pair<Int, String>> =
        genreCache[type] ?: tmdb.call { genres(type.path) }.genres.map { it.id to it.name }.also { genreCache[type] = it }

    /** Serviços de streaming do Brasil (filmes + séries), para "Meus serviços". */
    suspend fun providerList(): List<Provider> {
        val m = tmdb.call { providerList("movie") }.results
        val t = tmdb.call { providerList("tv") }.results
        return (m + t).distinctBy { it.id }.sortedBy { it.priority }.map { Provider(it.id, it.name, it.logoPath, it.priority) }
    }

    suspend fun pruneCache() = db.titles().pruneUnlisted(now() - 30L * 24 * 3600 * 1000)
}
