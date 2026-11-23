package dev.viko.sessao.data.trakt

import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.ListEntry
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.ScoreSource
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.domain.WatchedEpisode
import java.time.Instant
import kotlin.math.roundToInt

/** Regras da sincronização com o Trakt, puras (sem rede nem banco) para testar. */
object TraktRules {
    fun parseTime(s: String?): Long? = s?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    fun formatTime(ms: Long): String = Instant.ofEpochMilli(ms).toString()

    /** Trakt dá nota de 1 a 10; aqui a nota vai de 0 a 100. */
    fun fromTrakt(rating: Int): Int = (rating * 10).coerceIn(0, 100)
    fun toTrakt(raw: Int): Int? = if (raw <= 0) null else (raw / 10.0).roundToInt().coerceIn(1, 10)

    /** Episódios do histórico agrupados por série (id do TMDB). Itens sem id do TMDB ficam de fora. */
    fun episodesByShow(items: List<HistoryItem>): Map<Int, List<Pair<Ep, Long>>> =
        items.filter { it.type == "episode" && it.episode != null }
            .mapNotNull { h ->
                val show = h.show?.ids?.tmdb ?: return@mapNotNull null
                val e = h.episode!!
                Triple(show, Ep(e.season, e.number), parseTime(h.watchedAt) ?: 0L)
            }
            .groupBy({ it.first }, { it.second to it.third })

    /** Nota vinda do Trakt: vale se for mais nova que a última nota dada aqui. */
    fun applyRating(e: ListEntry, rating: Int, ratedAt: Long): ListEntry =
        if (ratedAt > e.scoreUpdatedAt && fromTrakt(rating) != e.scoreRaw) e.copy(scoreRaw = fromTrakt(rating), scoreSource = ScoreSource.APP, scoreUpdatedAt = ratedAt)
        else e

    /**
     * O que mandar para o Trakt: só o que mudou aqui depois de [since], fora o que acabou de chegar de lá
     * ([pulled]). Desmarcar ou remover não vai (para não apagar nada lá sem querer).
     */
    data class Push(val history: SyncBody, val ratings: SyncBody, val watchlist: SyncBody) {
        val isEmpty: Boolean get() = history.movies.isEmpty() && history.shows.isEmpty() &&
            ratings.movies.isEmpty() && ratings.shows.isEmpty() && watchlist.movies.isEmpty() && watchlist.shows.isEmpty()
    }

    fun push(
        entries: List<ListEntry>,
        watched: List<WatchedEpisode>,
        since: Long,
        pulled: Set<TitleKey>,
        pulledEpisodes: Set<Pair<Int, Ep>>,
    ): Push {
        val byKey = entries.associateBy { it.key }
        // Episódios marcados aqui depois da última sincronização.
        val eps = watched.filter { it.watchedAt > since && (it.showId to it.ep) !in pulledEpisodes && byKey.containsKey(TitleKey(MediaType.TV, it.showId)) }
        val shows = eps.groupBy { it.showId }.map { (show, list) ->
            SyncShow(
                ids = TraktIds(tmdb = show),
                seasons = list.groupBy { it.season }.toSortedMap().map { (s, l) ->
                    SyncSeason(s, l.sortedBy { it.episode }.map { SyncEpisode(it.episode, formatTime(it.watchedAt)) })
                },
            )
        }
        val movies = entries.filter {
            it.type == MediaType.MOVIE && it.status == WatchStatus.COMPLETED && (it.finishedAt ?: 0) > since && it.key !in pulled
        }.map { SyncMovie(TraktIds(tmdb = it.id), watchedAt = formatTime(it.finishedAt!!)) }

        val rated = entries.filter { it.scoreRaw > 0 && it.scoreUpdatedAt > since && it.key !in pulled }
        val ratings = SyncBody(
            movies = rated.filter { it.type == MediaType.MOVIE }.map { SyncMovie(TraktIds(tmdb = it.id), rating = toTrakt(it.scoreRaw), ratedAt = formatTime(it.scoreUpdatedAt)) },
            shows = rated.filter { it.type == MediaType.TV }.map { SyncShow(TraktIds(tmdb = it.id), rating = toTrakt(it.scoreRaw), ratedAt = formatTime(it.scoreUpdatedAt)) },
        )
        val planned = entries.filter { it.status == WatchStatus.PLANNING && it.addedAt > since && it.key !in pulled }
        val watchlist = SyncBody(
            movies = planned.filter { it.type == MediaType.MOVIE }.map { SyncMovie(TraktIds(tmdb = it.id)) },
            shows = planned.filter { it.type == MediaType.TV }.map { SyncShow(TraktIds(tmdb = it.id)) },
        )
        return Push(SyncBody(movies = movies, shows = shows), ratings, watchlist)
    }
}
