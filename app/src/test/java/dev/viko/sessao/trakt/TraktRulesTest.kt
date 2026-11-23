package dev.viko.sessao.trakt

import dev.viko.sessao.data.trakt.HistoryItem
import dev.viko.sessao.data.trakt.TraktEpisode
import dev.viko.sessao.data.trakt.TraktIds
import dev.viko.sessao.data.trakt.TraktRules
import dev.viko.sessao.data.trakt.TraktShow
import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.ListEntry
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.domain.WatchedEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraktRulesTest {
    @Test fun scoreConversion() {
        assertEquals(80, TraktRules.fromTrakt(8))
        assertEquals(9, TraktRules.toTrakt(85))
        assertEquals(10, TraktRules.toTrakt(100))
        assertEquals(1, TraktRules.toTrakt(2))
        assertNull(TraktRules.toTrakt(0))
    }

    @Test fun historyGroupedByShow() {
        fun ep(show: Int?, s: Int, e: Int) = HistoryItem(
            type = "episode", watchedAt = "2026-11-20T21:00:00.000Z",
            show = TraktShow(ids = TraktIds(tmdb = show)), episode = TraktEpisode(s, e),
        )
        val map = TraktRules.episodesByShow(listOf(ep(95396, 1, 1), ep(95396, 1, 2), ep(1399, 2, 3), ep(null, 1, 1)))
        assertEquals(setOf(95396, 1399), map.keys)
        assertEquals(listOf(Ep(1, 1), Ep(1, 2)), map.getValue(95396).map { it.first })
        assertTrue(map.getValue(1399).first().second > 0)
    }

    private fun entry(type: MediaType, id: Int, status: WatchStatus, raw: Int = 0, scoreAt: Long = 0, added: Long = 0, finished: Long? = null) =
        ListEntry(type = type, id = id, status = status, scoreRaw = raw, scoreUpdatedAt = scoreAt, addedAt = added, finishedAt = finished, updatedAt = 0)

    @Test fun newestRatingWins() {
        val e = entry(MediaType.MOVIE, 1, WatchStatus.COMPLETED, raw = 60, scoreAt = 1000)
        assertEquals(90, TraktRules.applyRating(e, 9, 2000).scoreRaw)
        assertEquals(60, TraktRules.applyRating(e, 9, 500).scoreRaw)
    }

    @Test fun pushOnlyLocalChangesSinceLastSync() {
        val since = 1000L
        val entries = listOf(
            entry(MediaType.TV, 10, WatchStatus.WATCHING, raw = 70, scoreAt = 1500),
            entry(MediaType.MOVIE, 20, WatchStatus.COMPLETED, finished = 2000),
            entry(MediaType.MOVIE, 21, WatchStatus.COMPLETED, finished = 500),
            entry(MediaType.MOVIE, 22, WatchStatus.PLANNING, added = 3000),
            entry(MediaType.MOVIE, 23, WatchStatus.COMPLETED, finished = 4000),
        )
        val watched = listOf(
            WatchedEpisode(10, 1, 1, 0, 900),
            WatchedEpisode(10, 1, 2, 0, 1100),
            WatchedEpisode(10, 1, 3, 0, 1200),
        )
        // O filme 23 e o episódio 1x3 acabaram de chegar do Trakt: não voltam para lá.
        val plan = TraktRules.push(entries, watched, since, setOf(TitleKey(MediaType.MOVIE, 23)), setOf(10 to Ep(1, 3)))
        val show = plan.history.shows.single()
        assertEquals(10, show.ids.tmdb)
        assertEquals(listOf(2), show.seasons!!.single().episodes.map { it.number })
        assertEquals(listOf(20), plan.history.movies.map { it.ids.tmdb })
        assertEquals(7, plan.ratings.shows.single().rating)
        assertEquals(listOf(22), plan.watchlist.movies.map { it.ids.tmdb })
    }

    @Test fun nothingToPush() {
        val plan = TraktRules.push(emptyList(), emptyList(), 0, emptySet(), emptySet())
        assertTrue(plan.isEmpty)
    }
}
