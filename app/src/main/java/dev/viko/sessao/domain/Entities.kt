package dev.viko.sessao.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Dados de um título vindos do TMDB, guardados para abrir sem internet. */
@Serializable
@Entity(tableName = "titles", primaryKeys = ["type", "id"])
data class Title(
    val type: MediaType,
    val id: Int,
    val name: String,
    val originalName: String? = null,
    val year: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    /** Cor dominante do pôster (ARGB), extraída com a Palette. */
    val posterColor: Int? = null,
    /** Filme: duração em minutos. Série: duração média de um episódio. */
    val runtime: Int? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    val genres: List<String> = emptyList(),
    val genreIds: List<Int> = emptyList(),
    /** Status da produção como o TMDB manda (Returning Series, Ended, Released...). */
    val productionStatus: String? = null,
    val inProduction: Boolean = false,
    val lastAired: AiredEpisode? = null,
    val nextEpisode: AiredEpisode? = null,
    val voteAverage: Double? = null,
    val overview: String = "",
    val releaseDate: String? = null,
    /** Série: emissora. Filme: estúdio. */
    val network: String? = null,
    /** Série: criação. Filme: direção. */
    val creators: List<String> = emptyList(),
    val providers: WatchProviders? = null,
    val providersUpdatedAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val key: TitleKey get() = TitleKey(type, id)

    /** Série encerrada (ou cancelada); filme é sempre "encerrado". */
    val ended: Boolean get() = type == MediaType.MOVIE || (!inProduction && productionStatus in setOf("Ended", "Canceled"))

    val totalEpisodes: Int get() = Progress.totalEpisodes(seasons)
}

/** Um item da minha lista. Nota sempre de 0 a 100 (0 = sem nota). */
@Serializable
@Entity(tableName = "entries", primaryKeys = ["type", "id"])
data class ListEntry(
    val type: MediaType,
    val id: Int,
    val status: WatchStatus,
    val scoreRaw: Int = 0,
    val scoreSource: ScoreSource = ScoreSource.APP,
    /** Quando a nota mudou (para resolver conflito com o Letterboxd). */
    val scoreUpdatedAt: Long = 0,
    val note: String = "",
    val addedAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    /** Vezes que vi (terminei, ou comecei a rever). */
    val timesWatched: Int = 0,
    /** Rodada atual dos episódios marcados: rever começa uma nova sem apagar a anterior. */
    val cycle: Int = 0,
    val updatedAt: Long,
) {
    val key: TitleKey get() = TitleKey(type, id)
}

/** Episódio marcado como visto, na rodada [cycle]. */
@Serializable
@Entity(tableName = "watched", primaryKeys = ["showId", "season", "episode", "cycle"])
data class WatchedEpisode(
    val showId: Int,
    val season: Int,
    val episode: Int,
    val cycle: Int,
    val watchedAt: Long,
) {
    val ep: Ep get() = Ep(season, episode)
}

/** Episódio de uma temporada (cache do TMDB, para o detalhe e a Agenda). */
@Entity(tableName = "episodes", primaryKeys = ["showId", "season", "episode"])
data class EpisodeInfo(
    val showId: Int,
    val season: Int,
    val episode: Int,
    val name: String = "",
    /** aaaa-mm-dd, como o TMDB manda. */
    val airDate: String? = null,
    val runtime: Int? = null,
    val stillPath: String? = null,
    val overview: String = "",
) {
    val ep: Ep get() = Ep(season, episode)
}

/** Entradas do feed do Letterboxd já lidas (pelo guid), para não contar duas vezes. */
@Entity(tableName = "letterboxd_seen")
data class LetterboxdSeen(@PrimaryKey val guid: String, val processedAt: Long)

/** Avisos já enviados, para a tela de notificações. */
@Entity(tableName = "notifications")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val title: String,
    val text: String,
    val type: MediaType? = null,
    val tmdbId: Int? = null,
)

/** Item da lista com os dados do título e os episódios marcados na rodada atual. */
data class Item(
    val entry: ListEntry,
    val title: Title?,
    val watched: Set<Ep> = emptySet(),
) {
    val key: TitleKey get() = entry.key
    val type: MediaType get() = entry.type
    val seasons: List<SeasonInfo> get() = title?.seasons.orEmpty()
    val watchedMain: Int get() = Progress.watchedMain(watched, seasons)
    val total: Int get() = Progress.totalEpisodes(seasons)
    val ended: Boolean get() = title?.ended ?: false

    /** Episódios lançados que ainda não vi. */
    val remaining: Int get() = Progress.remaining(watched, seasons, title?.lastAired?.ep, ended)

    /** Próximo episódio a marcar e se já saiu. */
    val next: Ep? get() = Progress.next(watched, seasons)
    val nextAired: Boolean get() = next?.let { Progress.isAired(it, title?.lastAired?.ep, ended) } ?: false

    val progressText: String get() = Progress.progressText(watched, seasons)

    /** Tem episódio novo esperando (série em andamento com episódios lançados não vistos). */
    val hasNew: Boolean get() = type == MediaType.TV && entry.status.active && remaining > 0 && watchedMain > 0
}
