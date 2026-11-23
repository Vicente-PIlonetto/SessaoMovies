package dev.viko.sessao.data.api

import dev.viko.sessao.domain.AiredEpisode
import dev.viko.sessao.domain.EpisodeInfo
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.Offer
import dev.viko.sessao.domain.OfferKind
import dev.viko.sessao.domain.Provider
import dev.viko.sessao.domain.SeasonInfo
import dev.viko.sessao.domain.Title
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchProviders

/** Item de busca, listas do Descobrir e "semelhantes". */
data class TitleSummary(
    val key: TitleKey,
    val name: String,
    val originalName: String?,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double?,
    val genreIds: List<Int>,
    val overview: String,
)

data class CastMember(val id: Int, val name: String, val character: String?, val profilePath: String?)

/** Partes do detalhe que só existem com internet (não vão para o Room). */
data class DetailExtras(val cast: List<CastMember> = emptyList(), val similar: List<TitleSummary> = emptyList())

private fun year(date: String?): Int? = date?.take(4)?.toIntOrNull()

/** [fallback] vale para listas de um tipo só (sem media_type). Pessoas ficam de fora. */
fun SummaryDto.toSummary(fallback: MediaType? = null): TitleSummary? {
    val type = when (mediaType) {
        "movie" -> MediaType.MOVIE
        "tv" -> MediaType.TV
        null -> fallback ?: return null
        else -> return null
    }
    val name = (if (type == MediaType.MOVIE) title else this.name) ?: title ?: this.name ?: return null
    return TitleSummary(
        key = TitleKey(type, id),
        name = name,
        originalName = (if (type == MediaType.MOVIE) originalTitle else originalName)?.takeIf { it != name },
        year = year(if (type == MediaType.MOVIE) releaseDate else firstAirDate),
        posterPath = posterPath,
        backdropPath = backdropPath,
        voteAverage = voteAverage?.takeIf { it > 0 },
        genreIds = genreIds,
        overview = overview.orEmpty(),
    )
}

/** Título mínimo a partir de um resumo, para adicionar à lista antes de baixar o detalhe. */
fun TitleSummary.toTitle(): Title = Title(
    type = key.type, id = key.id, name = name, originalName = originalName, year = year,
    posterPath = posterPath, backdropPath = backdropPath, voteAverage = voteAverage, genreIds = genreIds,
    overview = overview, updatedAt = 0, providersUpdatedAt = 0,
)

fun WatchProvidersDto?.toProviders(): WatchProviders? {
    val br = this?.results?.get("BR") ?: return if (this == null) null else WatchProviders()
    fun List<ProviderDto>.map(kind: OfferKind) = map { Offer(kind, Provider(it.id, it.name, it.logoPath, it.priority)) }
    return WatchProviders(
        link = br.link,
        offers = br.flatrate.map(OfferKind.FLATRATE) + (br.free + br.ads).map(OfferKind.FREE) +
            br.rent.map(OfferKind.RENT) + br.buy.map(OfferKind.BUY),
    )
}

fun EpisodeDto.toAired() = AiredEpisode(season, episode, airDate?.takeIf { it.isNotBlank() }, name)

fun EpisodeDto.toInfo(showId: Int) = EpisodeInfo(
    showId = showId, season = season, episode = episode, name = name.orEmpty(),
    airDate = airDate?.takeIf { it.isNotBlank() }, runtime = runtime, stillPath = stillPath, overview = overview.orEmpty(),
)

fun MovieDetailDto.toTitle(old: Title?, now: Long, englishOverview: String? = null): Title = Title(
    type = MediaType.MOVIE,
    id = id,
    name = title,
    originalName = originalTitle?.takeIf { it != title },
    year = year(releaseDate),
    posterPath = posterPath,
    backdropPath = backdropPath,
    posterColor = old?.posterColor?.takeIf { old.posterPath == posterPath },
    runtime = runtime?.takeIf { it > 0 },
    genres = genres.map { it.name },
    genreIds = genres.map { it.id },
    productionStatus = status,
    inProduction = false,
    voteAverage = voteAverage?.takeIf { it > 0 },
    overview = overview?.takeIf { it.isNotBlank() } ?: englishOverview.orEmpty(),
    releaseDate = releaseDate?.takeIf { it.isNotBlank() },
    network = companies.firstOrNull()?.name,
    creators = credits?.crew.orEmpty().filter { it.job == "Director" }.map { it.name }.distinct(),
    providers = providers.toProviders() ?: old?.providers,
    providersUpdatedAt = if (providers != null) now else old?.providersUpdatedAt ?: 0,
    updatedAt = now,
)

fun TvDetailDto.toTitle(old: Title?, now: Long, englishOverview: String? = null): Title = Title(
    type = MediaType.TV,
    id = id,
    name = name,
    originalName = originalName?.takeIf { it != name },
    year = year(firstAirDate),
    posterPath = posterPath,
    backdropPath = backdropPath,
    posterColor = old?.posterColor?.takeIf { old.posterPath == posterPath },
    runtime = episodeRunTime.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: lastEpisode?.runtime,
    seasons = seasons.map { SeasonInfo(it.number, it.episodeCount, it.name, it.airDate?.takeIf { d -> d.isNotBlank() }, it.posterPath) },
    genres = genres.map { it.name },
    genreIds = genres.map { it.id },
    productionStatus = status,
    inProduction = inProduction,
    lastAired = lastEpisode?.toAired(),
    nextEpisode = nextEpisode?.toAired(),
    voteAverage = voteAverage?.takeIf { it > 0 },
    overview = overview?.takeIf { it.isNotBlank() } ?: englishOverview.orEmpty(),
    releaseDate = firstAirDate?.takeIf { it.isNotBlank() },
    network = networks.firstOrNull()?.name,
    creators = createdBy.map { it.name },
    providers = providers.toProviders() ?: old?.providers,
    providersUpdatedAt = if (providers != null) now else old?.providersUpdatedAt ?: 0,
    updatedAt = now,
)

fun CreditsDto?.toCast(): List<CastMember> = this?.cast.orEmpty().sortedBy { it.order }.take(15)
    .map { CastMember(it.id, it.name, it.character ?: it.roles.firstOrNull()?.character, it.profilePath) }
