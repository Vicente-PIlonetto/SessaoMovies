package dev.viko.sessao.data.trakt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Respostas e corpos da API do Trakt (só os campos usados). Datas em ISO 8601 (UTC).

@Serializable
data class TraktIds(val trakt: Long? = null, val slug: String? = null, val tmdb: Int? = null, val imdb: String? = null)

@Serializable
data class TraktShow(val title: String? = null, val year: Int? = null, val ids: TraktIds = TraktIds())

@Serializable
data class TraktMovie(val title: String? = null, val year: Int? = null, val ids: TraktIds = TraktIds())

@Serializable
data class TraktEpisode(val season: Int = 0, val number: Int = 0, val title: String? = null, val ids: TraktIds = TraktIds())

@Serializable
data class HistoryItem(
    val id: Long = 0,
    @SerialName("watched_at") val watchedAt: String? = null,
    val type: String = "",
    val episode: TraktEpisode? = null,
    val show: TraktShow? = null,
    val movie: TraktMovie? = null,
)

@Serializable
data class RatingItem(
    @SerialName("rated_at") val ratedAt: String? = null,
    val rating: Int = 0,
    val type: String = "",
    val show: TraktShow? = null,
    val movie: TraktMovie? = null,
)

@Serializable
data class WatchlistItem(
    @SerialName("listed_at") val listedAt: String? = null,
    val type: String = "",
    val show: TraktShow? = null,
    val movie: TraktMovie? = null,
)

@Serializable
data class WatchedMovie(
    val plays: Int = 0,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    val movie: TraktMovie = TraktMovie(),
)

@Serializable
data class AvatarImage(val full: String? = null)

@Serializable
data class UserImages(val avatar: AvatarImage? = null)

@Serializable
data class TraktUser(
    val username: String = "",
    val name: String? = null,
    val private: Boolean = false,
    val vip: Boolean = false,
    val ids: TraktIds = TraktIds(),
    val images: UserImages? = null,
)

@Serializable
data class FollowItem(@SerialName("followed_at") val followedAt: String? = null, val user: TraktUser = TraktUser())

@Serializable
data class DeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class DeviceTokenRequest(val code: String, @SerialName("client_id") val clientId: String)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("grant_type") val grantType: String = "refresh_token",
    @SerialName("redirect_uri") val redirectUri: String = "urn:ietf:wg:oauth:2.0:oob",
)

// ---------- Envio (POST /sync/history, /sync/ratings, /sync/watchlist) ----------

@Serializable
data class SyncEpisode(val number: Int, @SerialName("watched_at") val watchedAt: String? = null)

@Serializable
data class SyncSeason(val number: Int, val episodes: List<SyncEpisode>)

@Serializable
data class SyncShow(
    val ids: TraktIds,
    val seasons: List<SyncSeason>? = null,
    val rating: Int? = null,
    @SerialName("rated_at") val ratedAt: String? = null,
)

@Serializable
data class SyncMovie(
    val ids: TraktIds,
    @SerialName("watched_at") val watchedAt: String? = null,
    val rating: Int? = null,
    @SerialName("rated_at") val ratedAt: String? = null,
)

@Serializable
data class SyncBody(val movies: List<SyncMovie> = emptyList(), val shows: List<SyncShow> = emptyList())
