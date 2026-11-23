package dev.viko.sessao.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    private val seasons = listOf(SeasonInfo(0, 3), SeasonInfo(1, 10), SeasonInfo(2, 8), SeasonInfo(3, 6))

    @Test fun plusOneStartsAtFirstEpisode() {
        assertEquals(Ep(1, 1), Progress.next(emptySet(), seasons))
    }

    @Test fun plusOneTurnsTheSeason() {
        assertEquals(Ep(3, 1), Progress.next(setOf(Ep(2, 8)), seasons))
        assertEquals(Ep(2, 1), Progress.next(Progress.upTo(Ep(1, 10), seasons), seasons))
    }

    @Test fun plusOneEndsAtLastEpisode() {
        assertNull(Progress.next(setOf(Ep(3, 6)), seasons))
    }

    @Test fun specialsAreOutOfTheCount() {
        assertEquals(24, Progress.totalEpisodes(seasons))
        val watched = setOf(Ep(0, 1), Ep(0, 2), Ep(1, 1))
        assertEquals(1, Progress.watchedMain(watched, seasons))
        assertEquals("T1 · E1", Progress.progressText(watched, seasons))
        assertEquals(listOf(Ep(0, 2)), Progress.upTo(Ep(0, 2), seasons))
    }

    @Test fun upToMarksPreviousSeasons() {
        val marked = Progress.upTo(Ep(2, 3), seasons)
        assertEquals(13, marked.size)
        assertTrue(Ep(1, 10) in marked)
        assertFalse(Ep(2, 4) in marked)
    }

    @Test fun remainingCountsOnlyAired() {
        // Lançado até T3 · E2.
        val watched = Progress.upTo(Ep(2, 8), seasons)
        assertEquals(2, Progress.remaining(watched, seasons, Ep(3, 2), ended = false))
        assertEquals(0, Progress.remaining(Progress.upTo(Ep(3, 2), seasons), seasons, Ep(3, 2), ended = false))
        // Encerrada sem data do último: tudo conta como lançado.
        assertEquals(24, Progress.remaining(emptySet(), seasons, null, ended = true))
    }

    @Test fun unairedNextIsBlocked() {
        val watched = Progress.upTo(Ep(3, 2), seasons)
        val next = Progress.next(watched, seasons)!!
        assertFalse(Progress.isAired(next, Ep(3, 2), ended = false))
    }

    @Test fun progressTextWithoutAnything() {
        assertEquals("T1 · E0", Progress.progressText(emptySet(), seasons))
    }
}
