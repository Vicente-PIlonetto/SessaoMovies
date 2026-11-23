package dev.viko.sessao.trakt

import dev.viko.sessao.data.trakt.FeedRules
import dev.viko.sessao.data.trakt.HistoryItem
import dev.viko.sessao.data.trakt.TraktEpisode
import dev.viko.sessao.data.trakt.TraktIds
import dev.viko.sessao.data.trakt.TraktMovie
import dev.viko.sessao.data.trakt.TraktShow
import dev.viko.sessao.data.trakt.TraktUser
import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedRulesTest {
    private val ana = TraktUser(username = "ana")
    private val bia = TraktUser(username = "bia")

    private fun ep(s: Int, e: Int, at: String, show: Int = 95396) = HistoryItem(
        type = "episode", watchedAt = at, show = TraktShow("Severance", ids = TraktIds(tmdb = show)), episode = TraktEpisode(s, e),
    )
    private fun movie(id: Int, at: String) = HistoryItem(type = "movie", watchedAt = at, movie = TraktMovie("Filme", ids = TraktIds(tmdb = id)))

    @Test fun bingeBecomesOneLine() {
        val items = listOf(
            ep(1, 3, "2026-11-22T23:00:00.000Z"), ep(1, 2, "2026-11-22T22:10:00.000Z"), ep(1, 1, "2026-11-22T21:20:00.000Z"),
            // Mais de 6 h antes: outra linha.
            ep(1, 4, "2026-11-20T10:00:00.000Z"),
        )
        val feed = FeedRules.merge(listOf(FeedRules.entries(ana, items)))
        assertEquals(2, feed.size)
        assertEquals(listOf(Ep(1, 1), Ep(1, 2), Ep(1, 3)), feed[0].episodes)
        assertEquals("T1 · E1 a E3", FeedRules.episodesLabel(feed[0].episodes))
    }

    @Test fun mixedPeopleSortedByTime() {
        val a = FeedRules.entries(ana, listOf(movie(1, "2026-11-22T20:00:00.000Z")))
        val b = FeedRules.entries(bia, listOf(movie(2, "2026-11-22T21:00:00.000Z"), ep(2, 1, "2026-11-21T21:00:00.000Z")))
        val feed = FeedRules.merge(listOf(a, b))
        assertEquals(listOf("bia", "ana", "bia"), feed.map { it.user.username })
        assertEquals(MediaType.MOVIE, feed[0].key.type)
    }

    @Test fun itemsWithoutTmdbAreSkipped() {
        val items = listOf(HistoryItem(type = "movie", watchedAt = "2026-11-22T20:00:00.000Z", movie = TraktMovie("Sem id")))
        assertEquals(0, FeedRules.entries(ana, items).size)
    }

    @Test fun labels() {
        assertEquals("T2 · E5", FeedRules.episodesLabel(listOf(Ep(2, 5))))
        assertEquals("T1 · E10 a T2 · E1", FeedRules.episodesLabel(listOf(Ep(1, 10), Ep(2, 1))))
    }
}
