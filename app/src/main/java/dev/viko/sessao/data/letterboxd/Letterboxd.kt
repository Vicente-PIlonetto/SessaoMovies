package dev.viko.sessao.data.letterboxd

import dev.viko.sessao.domain.ListEntry
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.ScoreSource
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.domain.score.Scores
import org.w3c.dom.Element
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Uma entrada do feed RSS público do Letterboxd (só filmes). */
data class FeedEntry(
    val guid: String,
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    /** 0,5 a 5; null = sem nota. */
    val rating: Double?,
    val watchedDate: LocalDate?,
    val rewatch: Boolean,
    /** Quando a entrada foi publicada (epoch em ms), usado no conflito de nota. */
    val publishedAt: Long,
)

/** Leitura do feed https://letterboxd.com/{usuario}/rss/. */
object LetterboxdFeed {
    fun url(user: String) = "https://letterboxd.com/${user.trim().trimStart('@')}/rss/"

    /** Itens sem tmdb:movieId (listas, por exemplo) ficam de fora. */
    fun parse(xml: String): List<FeedEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Feed de fora: nada de DTD nem entidades externas.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { isExpandEntityReferences = false }
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val items = doc.getElementsByTagName("item")
        return (0 until items.length).mapNotNull { i ->
            val el = items.item(i) as? Element ?: return@mapNotNull null
            val tmdb = el.child("movieId")?.toIntOrNull() ?: return@mapNotNull null
            val guid = el.child("guid") ?: return@mapNotNull null
            FeedEntry(
                guid = guid,
                tmdbId = tmdb,
                title = el.child("filmTitle") ?: el.child("title").orEmpty(),
                year = el.child("filmYear")?.toIntOrNull(),
                rating = el.child("memberRating")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 },
                watchedDate = el.child("watchedDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                rewatch = el.child("rewatch").equals("Yes", ignoreCase = true),
                publishedAt = el.child("pubDate")?.let { parseRfc1123(it) } ?: 0L,
            )
        }
    }

    /** Remove entradas já processadas e guids repetidos no próprio feed. */
    fun unseen(entries: List<FeedEntry>, seen: Set<String>): List<FeedEntry> =
        entries.distinctBy { it.guid }.filter { it.guid !in seen }

    /**
     * Aplica uma entrada do feed ao item da lista (ou cria o item).
     * O filme vira Visto; rewatch soma 1 nas vezes vistas; a nota vale se for mais nova que a minha última edição.
     */
    fun apply(current: ListEntry?, e: FeedEntry, now: Long): ListEntry {
        val watchedAt = e.watchedDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: e.publishedAt.takeIf { it > 0 } ?: now
        val base = current ?: ListEntry(
            type = MediaType.MOVIE, id = e.tmdbId, status = WatchStatus.COMPLETED,
            addedAt = now, updatedAt = now,
        )
        val times = when {
            current == null -> 1
            e.rewatch -> base.timesWatched.coerceAtLeast(1) + 1
            else -> base.timesWatched.coerceAtLeast(1)
        }
        var out = base.copy(
            status = WatchStatus.COMPLETED,
            timesWatched = times,
            startedAt = base.startedAt ?: watchedAt,
            finishedAt = maxOf(base.finishedAt ?: 0L, watchedAt),
            updatedAt = now,
        )
        val rating = e.rating
        val ratedAt = e.publishedAt.takeIf { it > 0 } ?: now
        if (rating != null && (current == null || ratedAt > base.scoreUpdatedAt)) {
            out = out.copy(scoreRaw = Scores.fromLetterboxd(rating), scoreSource = ScoreSource.LETTERBOXD, scoreUpdatedAt = ratedAt)
        }
        return out
    }

    private fun Element.child(local: String): String? {
        val nodes = getElementsByTagNameNS("*", local)
        val n = if (nodes.length > 0) nodes.item(0) else getElementsByTagName(local).item(0)
        return n?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Data do RSS. Se o dia da semana não bater com a data, ele é ignorado (a data vale). */
    fun parseRfc1123(s: String): Long? {
        runCatching { return ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        val noWeekday = s.trim().substringAfter(", ", s.trim())
        return runCatching {
            ZonedDateTime.parse(noWeekday, DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)).toInstant().toEpochMilli()
        }.getOrNull()
    }
}

/** Um filme do .zip exportado pelo Letterboxd, juntando diary, ratings, watched e watchlist. */
data class ImportFilm(
    val name: String,
    val year: Int?,
    val watchedDates: List<LocalDate> = emptyList(),
    val rating: Double? = null,
    val rewatches: Int = 0,
    val watched: Boolean = false,
    val watchlist: Boolean = false,
)

/** Leitura do .zip de Configurações > Dados > Exportar do Letterboxd. */
object LetterboxdExportZip {
    fun read(input: InputStream): List<ImportFilm> {
        val files = mutableMapOf<String, String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/')
                // Só os arquivos da raiz interessam (a pasta "deleted" e as listas ficam de fora).
                if (!e.isDirectory && !e.name.contains("/") && name.endsWith(".csv")) {
                    files[name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        return merge(
            diary = files["diary.csv"]?.let(Csv::parse).orEmpty(),
            ratings = files["ratings.csv"]?.let(Csv::parse).orEmpty(),
            watched = files["watched.csv"]?.let(Csv::parse).orEmpty(),
            watchlist = files["watchlist.csv"]?.let(Csv::parse).orEmpty(),
        )
    }

    fun merge(
        diary: List<Map<String, String>>,
        ratings: List<Map<String, String>>,
        watched: List<Map<String, String>>,
        watchlist: List<Map<String, String>>,
    ): List<ImportFilm> {
        data class K(val name: String, val year: Int?)
        fun key(r: Map<String, String>) = K(r["Name"].orEmpty().trim(), r["Year"]?.trim()?.toIntOrNull())
        val out = linkedMapOf<K, ImportFilm>()
        fun upd(k: K, f: (ImportFilm) -> ImportFilm) { if (k.name.isNotEmpty()) out[k] = f(out[k] ?: ImportFilm(k.name, k.year)) }

        watched.forEach { upd(key(it)) { f -> f.copy(watched = true) } }
        diary.forEach { r ->
            val date = (r["Watched Date"] ?: r["Date"])?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            val rewatch = r["Rewatch"].equals("Yes", ignoreCase = true)
            upd(key(r)) { f ->
                f.copy(
                    watched = true,
                    watchedDates = (f.watchedDates + listOfNotNull(date)).sorted(),
                    rewatches = f.rewatches + if (rewatch) 1 else 0,
                    rating = r["Rating"]?.toDoubleOrNull() ?: f.rating,
                )
            }
        }
        // ratings.csv tem a nota atual (a do diário pode ser antiga).
        ratings.forEach { r -> upd(key(r)) { f -> f.copy(rating = r["Rating"]?.toDoubleOrNull() ?: f.rating) } }
        watchlist.forEach { upd(key(it)) { f -> f.copy(watchlist = true) } }
        return out.values.toList()
    }
}

/** Linha do CSV no formato do importador do Letterboxd. */
data class ExportRow(
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val raw: Int,
    val watchedDate: LocalDate?,
    val rewatch: Boolean,
)

object LetterboxdCsv {
    const val HEADER = "tmdbID,Title,Year,Rating,WatchedDate,Rewatch"

    fun build(rows: List<ExportRow>): String = buildString {
        append(HEADER).append('\n')
        rows.forEach { r ->
            val rating = Scores.toLetterboxd(r.raw)?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()
            append(r.tmdbId).append(',')
            append(Csv.quote(r.title)).append(',')
            append(r.year?.toString().orEmpty()).append(',')
            append(rating).append(',')
            append(r.watchedDate?.toString().orEmpty()).append(',')
            append(if (r.rewatch) "true" else "false").append('\n')
        }
    }

    /**
     * Filmes que entram na exportação: vistos ou com nota, mudados depois de [since]
     * (ou todos, com [all]).
     */
    fun select(entries: List<ListEntry>, since: Long, all: Boolean): List<ListEntry> =
        entries.filter { it.type == MediaType.MOVIE }
            .filter { it.status == WatchStatus.COMPLETED || it.scoreRaw > 0 }
            .filter { all || it.updatedAt > since }
}

/** CSV simples com aspas (o formato que o Letterboxd usa). */
object Csv {
    fun parse(text: String): List<Map<String, String>> {
        val rows = rows(text.removePrefix("﻿"))
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim() }
        return rows.drop(1).filter { r -> r.any { it.isNotBlank() } }.map { r -> header.indices.associate { header[it] to r.getOrElse(it) { "" } } }
    }

    fun rows(text: String): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row.add(cell.toString()); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.clear(); out.add(row); row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); out.add(row) }
        return out
    }

    fun quote(s: String): String = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
