package dev.viko.sessao.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Respostas da API v3 do TMDB. Só os campos usados; o resto é ignorado.

@Serializable
data class PageDto<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

/** Item de busca, listas e "semelhantes". Filmes usam title/release_date; séries, name/first_air_date. */
@Serializable
data class SummaryDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val overview: String? = null,
    val popularity: Double? = null,
)

@Serializable
data class GenreDto(val id: Int, val name: String)

@Serializable
data class GenreListDto(val genres: List<GenreDto> = emptyList())

@Serializable
data class CompanyDto(val id: Int = 0, val name: String = "")

@Serializable
data class CastDto(
    val id: Int,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 999,
    val roles: List<RoleDto> = emptyList(),
)

@Serializable
data class RoleDto(val character: String? = null)

@Serializable
data class CrewDto(val id: Int, val name: String = "", val job: String? = null)

@Serializable
data class CreditsDto(val cast: List<CastDto> = emptyList(), val crew: List<CrewDto> = emptyList())

@Serializable
data class ProviderDto(
    @SerialName("provider_id") val id: Int,
    @SerialName("provider_name") val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val priority: Int = 999,
)

@Serializable
data class CountryProvidersDto(
    val link: String? = null,
    val flatrate: List<ProviderDto> = emptyList(),
    val free: List<ProviderDto> = emptyList(),
    val ads: List<ProviderDto> = emptyList(),
    val rent: List<ProviderDto> = emptyList(),
    val buy: List<ProviderDto> = emptyList(),
)

@Serializable
data class WatchProvidersDto(val results: Map<String, CountryProvidersDto> = emptyMap())

@Serializable
data class ProviderListDto(val results: List<ProviderDto> = emptyList())

@Serializable
data class SeasonSummaryDto(
    @SerialName("season_number") val number: Int,
    @SerialName("episode_count") val episodeCount: Int = 0,
    val name: String = "",
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
data class EpisodeDto(
    @SerialName("season_number") val season: Int,
    @SerialName("episode_number") val episode: Int,
    val name: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val overview: String? = null,
)

@Serializable
data class SeasonDto(
    @SerialName("season_number") val number: Int = 0,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
data class ReleaseDatesDto(val results: List<CountryReleasesDto> = emptyList())

@Serializable
data class CountryReleasesDto(
    @SerialName("iso_3166_1") val country: String,
    @SerialName("release_dates") val dates: List<ReleaseDateDto> = emptyList(),
)

@Serializable
data class ReleaseDateDto(@SerialName("release_date") val date: String? = null, val type: Int = 0)

@Serializable
data class MovieDetailDto(
    val id: Int,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<GenreDto> = emptyList(),
    val status: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("production_companies") val companies: List<CompanyDto> = emptyList(),
    val credits: CreditsDto? = null,
    val similar: PageDto<SummaryDto>? = null,
    @SerialName("watch/providers") val providers: WatchProvidersDto? = null,
)

@Serializable
data class TvDetailDto(
    val id: Int,
    val name: String = "",
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val status: String? = null,
    @SerialName("in_production") val inProduction: Boolean = false,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val networks: List<CompanyDto> = emptyList(),
    @SerialName("created_by") val createdBy: List<CompanyDto> = emptyList(),
    val seasons: List<SeasonSummaryDto> = emptyList(),
    @SerialName("last_episode_to_air") val lastEpisode: EpisodeDto? = null,
    @SerialName("next_episode_to_air") val nextEpisode: EpisodeDto? = null,
    @SerialName("aggregate_credits") val credits: CreditsDto? = null,
    val similar: PageDto<SummaryDto>? = null,
    @SerialName("watch/providers") val providers: WatchProvidersDto? = null,
)
