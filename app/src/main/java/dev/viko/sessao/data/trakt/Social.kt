package dev.viko.sessao.data.trakt

import dev.viko.sessao.data.repo.TitleRepository
import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.TitleKey

/** Uma linha do feed: alguém viu um filme, um episódio ou vários episódios seguidos da mesma série. */
data class FeedEntry(
    val user: TraktUser,
    val at: Long,
    val key: TitleKey,
    val traktName: String,
    /** Episódios vistos, em ordem; vazio para filme. */
    val episodes: List<Ep> = emptyList(),
)

/** Linha do feed com o nome e o pôster do TMDB. */
data class FeedRow(val entry: FeedEntry, val name: String, val posterPath: String?, val posterColor: Int?)

data class Profile(
    val user: TraktUser,
    val stats: UserStats?,
    val history: List<FeedRow>,
    val ratings: List<RatedRow>,
    val isMe: Boolean,
    val following: Boolean,
)

data class RatedRow(val key: TitleKey, val raw: Int, val at: Long, val name: String, val posterPath: String?, val posterColor: Int?)

/** Regras do feed, puras para testar. */
object FeedRules {
    /** Janela para juntar episódios seguidos da mesma série numa linha só. */
    const val GROUP_WINDOW_MS = 6 * 3600 * 1000L

    fun entries(user: TraktUser, items: List<HistoryItem>): List<FeedEntry> = items.mapNotNull { h ->
        val at = TraktRules.parseTime(h.watchedAt) ?: return@mapNotNull null
        when {
            h.type == "movie" && h.movie?.ids?.tmdb != null ->
                FeedEntry(user, at, TitleKey(MediaType.MOVIE, h.movie.ids.tmdb), h.movie.title.orEmpty())
            h.type == "episode" && h.show?.ids?.tmdb != null && h.episode != null ->
                FeedEntry(user, at, TitleKey(MediaType.TV, h.show.ids.tmdb), h.show.title.orEmpty(), listOf(Ep(h.episode.season, h.episode.number)))
            else -> null
        }
    }

    /**
     * Junta tudo, do mais recente para o mais antigo. Episódios da mesma série vistos pela mesma pessoa
     * em sequência (até 6 h entre um e outro) viram uma linha.
     */
    fun merge(perUser: List<List<FeedEntry>>, limit: Int = 80): List<FeedEntry> {
        val grouped = perUser.flatMap { list ->
            val out = mutableListOf<FeedEntry>()
            list.sortedByDescending { it.at }.forEach { e ->
                val last = out.lastOrNull()
                if (last != null && e.episodes.isNotEmpty() && last.key == e.key && last.at - e.at <= GROUP_WINDOW_MS) {
                    out[out.lastIndex] = last.copy(episodes = (last.episodes + e.episodes).distinct().sorted())
                } else out += e
            }
            out
        }
        return grouped.sortedByDescending { it.at }.take(limit)
    }

    /** "T1 · E3", ou "T1 · E1 a E4" para vários seguidos. */
    fun episodesLabel(eps: List<Ep>): String {
        if (eps.isEmpty()) return ""
        if (eps.size == 1) return eps.first().label
        val first = eps.first()
        val last = eps.last()
        return if (first.season == last.season) "T${first.season} · E${first.episode} a E${last.episode}" else "${first.label} a ${last.label}"
    }
}

/** Parte social do Trakt: feed de quem eu sigo, perfis, busca e seguir. */
class SocialRepository(private val trakt: TraktRepository, private val titles: TitleRepository) {
    private val client get() = trakt.client
    val me: String? get() = trakt.session.value?.username

    suspend fun following(): List<TraktUser> = client.call { following("me") }.map { it.user }.filter { !it.deleted() }

    /** Feed montado com o histórico de cada pessoa que eu sigo (o feed pronto do Trakt é de acesso restrito). */
    suspend fun feed(onPartial: (List<FeedRow>) -> Unit = {}): List<FeedRow> {
        val people = following().filter { !it.private }.take(40)
        val per = mutableListOf<List<FeedEntry>>()
        var rows: List<FeedRow> = emptyList()
        for (u in people) {
            val slug = u.ids.slug ?: u.username
            val items = runCatching { client.call { userHistory(slug, 15) } }.getOrDefault(emptyList())
            per += FeedRules.entries(u, items)
            rows = FeedRules.merge(per).map { withTitle(it) }
            onPartial(rows)
        }
        return rows
    }

    suspend fun search(query: String): List<TraktUser> =
        client.call { searchUsers(query.trim()) }.mapNotNull { it.user }.filter { !it.deleted() }

    suspend fun profile(slug: String): Profile {
        val user = client.call { user(slug) }
        val isMe = slug.equals(me, ignoreCase = true)
        if (user.private && !isMe) return Profile(user, null, emptyList(), emptyList(), isMe, followsHim(slug))
        val stats = runCatching { client.call { stats(slug) } }.getOrNull()
        val history = runCatching { client.call { userHistory(slug, 20) } }.getOrDefault(emptyList())
        val ratings = runCatching { client.call { userRatings(slug, "all", 30) } }.getOrDefault(emptyList())
        val feed = FeedRules.merge(listOf(FeedRules.entries(user, history)), 20).map { withTitle(it) }
        val rated = ratings.filter { it.type == "movie" || it.type == "show" }.mapNotNull { r ->
            val id = (if (r.type == "movie") r.movie?.ids?.tmdb else r.show?.ids?.tmdb) ?: return@mapNotNull null
            val key = TitleKey(if (r.type == "movie") MediaType.MOVIE else MediaType.TV, id)
            val b = titles.basic(key, r.movie?.title ?: r.show?.title)
            RatedRow(key, TraktRules.fromTrakt(r.rating), TraktRules.parseTime(r.ratedAt) ?: 0, b.name, b.posterPath, b.posterColor)
        }.sortedByDescending { it.at }.take(20)
        return Profile(user, stats, feed, rated, isMe, if (isMe) false else followsHim(slug))
    }

    private suspend fun followsHim(slug: String): Boolean =
        runCatching { following().any { (it.ids.slug ?: it.username).equals(slug, ignoreCase = true) } }.getOrDefault(false)

    suspend fun follow(slug: String, on: Boolean) {
        val r = client.call { if (on) follow(slug) else unfollow(slug) }
        if (!r.isSuccessful) throw TraktError.Other(r.code())
    }

    private suspend fun withTitle(e: FeedEntry): FeedRow {
        val b = titles.basic(e.key, e.traktName)
        return FeedRow(e, b.name.ifBlank { e.traktName }, b.posterPath, b.posterColor)
    }

    private fun TraktUser.deleted() = username.isBlank()
}
