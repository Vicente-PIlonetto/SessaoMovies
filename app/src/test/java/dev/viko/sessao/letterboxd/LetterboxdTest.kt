package dev.viko.sessao.letterboxd

import dev.viko.sessao.data.letterboxd.Csv
import dev.viko.sessao.data.letterboxd.ExportRow
import dev.viko.sessao.data.letterboxd.LetterboxdCsv
import dev.viko.sessao.data.letterboxd.LetterboxdExportZip
import dev.viko.sessao.data.letterboxd.LetterboxdFeed
import dev.viko.sessao.domain.ListEntry
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.ScoreSource
import dev.viko.sessao.domain.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LetterboxdTest {
    private val xml = javaClass.classLoader!!.getResource("letterboxd_feed.xml")!!.readText()

    @Test fun readsFilmsAndSkipsLists() {
        val items = LetterboxdFeed.parse(xml)
        assertEquals(2, items.size)
        val dune = items[0]
        assertEquals(693134, dune.tmdbId)
        assertEquals("Dune: Part Two", dune.title)
        assertEquals(2024, dune.year)
        assertEquals(4.5, dune.rating!!, 0.0)
        assertEquals(LocalDate.of(2026, 11, 22), dune.watchedDate)
        assertFalse(dune.rewatch)
        assertTrue(dune.publishedAt > 0)
        val days = items[1]
        assertTrue(days.rewatch)
        assertNull(days.rating)
    }

    @Test fun pubDateWithWrongWeekdayStillReads() {
        assertTrue((LetterboxdFeed.parseRfc1123("Mon, 22 Nov 2026 21:10:00 -0300") ?: 0) > 0)
    }

    @Test fun repeatedEntriesCountOnce() {
        val items = LetterboxdFeed.parse(xml)
        val doubled = items + items
        assertEquals(2, LetterboxdFeed.unseen(doubled, emptySet()).size)
        assertEquals(listOf("letterboxd-watch-222"), LetterboxdFeed.unseen(items, setOf("letterboxd-review-111")).map { it.guid })
    }

    @Test fun newEntryBecomesWatchedWithScore() {
        val dune = LetterboxdFeed.parse(xml)[0]
        val e = LetterboxdFeed.apply(null, dune, now = 5)
        assertEquals(WatchStatus.COMPLETED, e.status)
        assertEquals(1, e.timesWatched)
        assertEquals(90, e.scoreRaw)
        assertEquals(ScoreSource.LETTERBOXD, e.scoreSource)
    }

    @Test fun rewatchAddsOne() {
        val days = LetterboxdFeed.parse(xml)[1]
        val cur = ListEntry(type = MediaType.MOVIE, id = 976893, status = WatchStatus.COMPLETED, timesWatched = 1, addedAt = 0, updatedAt = 0)
        assertEquals(2, LetterboxdFeed.apply(cur, days, now = 5).timesWatched)
    }

    @Test fun newestScoreWins() {
        val dune = LetterboxdFeed.parse(xml)[0]
        val base = ListEntry(type = MediaType.MOVIE, id = 693134, status = WatchStatus.PLANNING, scoreRaw = 60, addedAt = 0, updatedAt = 0)
        // Minha edição é mais antiga que o feed: vale a do Letterboxd.
        val older = LetterboxdFeed.apply(base.copy(scoreUpdatedAt = dune.publishedAt - 1000), dune, now = 5)
        assertEquals(90, older.scoreRaw)
        // Editei no app depois do feed: fica a minha.
        val newer = LetterboxdFeed.apply(base.copy(scoreUpdatedAt = dune.publishedAt + 1000), dune, now = 5)
        assertEquals(60, newer.scoreRaw)
        assertEquals(ScoreSource.APP, newer.scoreSource)
    }

    private fun zip(files: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun readsExportedZip() {
        val bytes = zip(mapOf(
            "diary.csv" to "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date\n" +
                "2026-01-02,\"Dune: Part Two\",2024,https://boxd.it/a,4,,,2026-01-01\n" +
                "2026-03-02,\"Dune: Part Two\",2024,https://boxd.it/a,4.5,Yes,,2026-03-01\n",
            "ratings.csv" to "Date,Name,Year,Letterboxd URI,Rating\n2026-03-02,\"Dune: Part Two\",2024,https://boxd.it/a,5\n",
            "watched.csv" to "Date,Name,Year,Letterboxd URI\n2025-05-05,\"Aftersun\",2022,https://boxd.it/b\n",
            "watchlist.csv" to "Date,Name,Year,Letterboxd URI\n2025-06-06,\"Paris, Texas\",1984,https://boxd.it/c\n",
            "deleted/diary.csv" to "Date,Name,Year\n2020-01-01,Apagado,2000\n",
        ))
        val films = LetterboxdExportZip.read(bytes.inputStream()).associateBy { it.name }
        assertEquals(3, films.size)
        val dune = films.getValue("Dune: Part Two")
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)), dune.watchedDates)
        assertEquals(1, dune.rewatches)
        assertEquals(5.0, dune.rating!!, 0.0)
        assertTrue(films.getValue("Aftersun").watched)
        val paris = films.getValue("Paris, Texas")
        assertTrue(paris.watchlist)
        assertFalse(paris.watched)
        assertEquals(1984, paris.year)
    }

    @Test fun buildsImportCsv() {
        val csv = LetterboxdCsv.build(listOf(
            ExportRow(693134, "Dune: Part Two", 2024, 92, LocalDate.of(2026, 11, 22), rewatch = true),
            ExportRow(1, "Paris, Texas", 1984, 0, null, rewatch = false),
            ExportRow(2, "Aftersun", 2022, 80, LocalDate.of(2025, 5, 5), rewatch = false),
        ))
        val lines = csv.trim().lines()
        assertEquals("tmdbID,Title,Year,Rating,WatchedDate,Rewatch", lines[0])
        assertEquals("693134,Dune: Part Two,2024,4.5,2026-11-22,true", lines[1])
        assertEquals("1,\"Paris, Texas\",1984,,,false", lines[2])
        assertEquals("2,Aftersun,2022,4,2025-05-05,false", lines[3])
        // E o CSV volta a ser lido igual.
        assertEquals("Paris, Texas", Csv.parse(csv)[1]["Title"])
    }

    @Test fun exportSelectsChangedSince() {
        fun e(id: Int, status: WatchStatus, raw: Int, updated: Long, type: MediaType = MediaType.MOVIE) =
            ListEntry(type = type, id = id, status = status, scoreRaw = raw, addedAt = 0, updatedAt = updated)
        val all = listOf(
            e(1, WatchStatus.COMPLETED, 0, 100), e(2, WatchStatus.PLANNING, 0, 100), e(3, WatchStatus.PLANNING, 80, 100),
            e(4, WatchStatus.COMPLETED, 70, 10), e(5, WatchStatus.COMPLETED, 70, 100, MediaType.TV),
        )
        assertEquals(listOf(1, 3), LetterboxdCsv.select(all, since = 50, all = false).map { it.id })
        assertEquals(listOf(1, 3, 4), LetterboxdCsv.select(all, since = 50, all = true).map { it.id })
    }
}
