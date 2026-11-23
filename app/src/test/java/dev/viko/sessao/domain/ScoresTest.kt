package dev.viko.sessao.domain

import dev.viko.sessao.domain.score.ScoreDisplay
import dev.viko.sessao.domain.score.ScoreSystem
import dev.viko.sessao.domain.score.Scores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoresTest {
    @Test fun sameValueInEverySystem() {
        assertEquals("85", Scores.text(85, ScoreSystem.POINT_100))
        assertEquals("8,5", Scores.text(85, ScoreSystem.POINT_10_DECIMAL))
        assertEquals("9", Scores.text(85, ScoreSystem.POINT_10))
        assertEquals("4,5", Scores.text(85, ScoreSystem.STARS_5))
        assertEquals("A−−", Scores.text(85, ScoreSystem.LETTERS))
        assertEquals("A", Scores.text(89, ScoreSystem.LETTERS))
        assertEquals(ScoreDisplay.Face(6, "Gostei muito"), Scores.display(89, ScoreSystem.SMILEY_7))
        assertEquals(ScoreDisplay.None, Scores.display(0, ScoreSystem.LETTERS))
    }

    @Test fun snapToTheChosenSystem() {
        assertEquals(71, Scores.snap(72, ScoreSystem.LETTERS))
        assertEquals(70, Scores.snap(72, ScoreSystem.STARS_5))
        assertEquals(70, Scores.snap(72, ScoreSystem.POINT_10))
        assertEquals(77, Scores.snap(72, ScoreSystem.SMILEY_7))
        assertEquals(94, Scores.snap(100, ScoreSystem.LETTERS))
    }

    @Test fun letterboxdStars() {
        assertEquals(70, Scores.fromLetterboxd(3.5))
        assertEquals(100, Scores.fromLetterboxd(5.0))
        assertEquals(10, Scores.fromLetterboxd(0.5))
        assertEquals(3.5, Scores.toLetterboxd(70)!!, 0.0)
        // 73 não cai em meia estrela: arredonda para 3,5.
        assertEquals(3.5, Scores.toLetterboxd(73)!!, 0.0)
        assertEquals(4.0, Scores.toLetterboxd(77)!!, 0.0)
        assertEquals(0.5, Scores.toLetterboxd(2)!!, 0.0)
        assertNull(Scores.toLetterboxd(0))
    }
}
