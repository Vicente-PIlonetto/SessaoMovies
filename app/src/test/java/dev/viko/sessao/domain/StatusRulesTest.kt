package dev.viko.sessao.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusRulesTest {
    @Test fun watchingWhileEpisodesRemain() {
        assertEquals(WatchStatus.WATCHING, StatusRules.afterProgress(WatchStatus.PLANNING, 3, 7, 24, ended = false))
    }

    @Test fun caughtUpWhenLastAiredIsMarked() {
        assertEquals(WatchStatus.CAUGHT_UP, StatusRules.afterProgress(WatchStatus.WATCHING, 20, 0, 24, ended = false))
    }

    @Test fun completedWhenEndedSeriesIsDone() {
        assertEquals(WatchStatus.COMPLETED, StatusRules.afterProgress(WatchStatus.WATCHING, 24, 0, 24, ended = true))
    }

    @Test fun rewatchingStaysUntilTheEnd() {
        assertEquals(WatchStatus.REWATCHING, StatusRules.afterProgress(WatchStatus.REWATCHING, 5, 19, 24, ended = true))
        assertEquals(WatchStatus.COMPLETED, StatusRules.afterProgress(WatchStatus.REWATCHING, 24, 0, 24, ended = true))
    }

    @Test fun unmarkingEverythingLeavesCaughtUp() {
        assertEquals(WatchStatus.WATCHING, StatusRules.afterProgress(WatchStatus.CAUGHT_UP, 0, 10, 24, ended = false))
    }

    @Test fun newEpisodeTurnsCaughtUpIntoWatching() {
        assertEquals(WatchStatus.WATCHING, StatusRules.afterRefresh(WatchStatus.CAUGHT_UP, 20, 1, 24, ended = false))
        assertEquals(WatchStatus.CAUGHT_UP, StatusRules.afterRefresh(WatchStatus.CAUGHT_UP, 20, 0, 24, ended = false))
        assertEquals(WatchStatus.PAUSED, StatusRules.afterRefresh(WatchStatus.PAUSED, 20, 3, 24, ended = false))
    }

    @Test fun caughtUpBecomesCompletedWhenSeriesEnds() {
        assertEquals(WatchStatus.COMPLETED, StatusRules.afterRefresh(WatchStatus.CAUGHT_UP, 24, 0, 24, ended = true))
    }
}
