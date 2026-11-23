package dev.viko.sessao.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryRulesTest {
    private fun entry(type: MediaType, status: WatchStatus, times: Int = 1, cycle: Int = 0) =
        ListEntry(type = type, id = 1, status = status, addedAt = 0, updatedAt = 0, timesWatched = times, cycle = cycle, finishedAt = 10)

    @Test fun rewatchSeriesStartsNewRound() {
        val out = EntryRules.rewatch(entry(MediaType.TV, WatchStatus.COMPLETED), now = 100)
        assertEquals(WatchStatus.REWATCHING, out.status)
        assertEquals(2, out.timesWatched)
        assertEquals(1, out.cycle)
        assertEquals(10L, out.finishedAt)
    }

    @Test fun rewatchMovieStaysWatched() {
        val out = EntryRules.rewatch(entry(MediaType.MOVIE, WatchStatus.COMPLETED, times = 2), now = 100)
        assertEquals(WatchStatus.COMPLETED, out.status)
        assertEquals(3, out.timesWatched)
        assertEquals(100L, out.finishedAt)
        assertEquals(0, out.cycle)
    }

    @Test fun completingCountsOnce() {
        val out = EntryRules.withStatus(entry(MediaType.TV, WatchStatus.WATCHING, times = 0), WatchStatus.COMPLETED, now = 50)
        assertEquals(1, out.timesWatched)
        assertEquals(50L, out.finishedAt)
        // Terminar um "rever" não soma de novo (já somou ao começar).
        val again = EntryRules.withStatus(entry(MediaType.TV, WatchStatus.REWATCHING, times = 2), WatchStatus.COMPLETED, now = 60)
        assertEquals(2, again.timesWatched)
    }
}
