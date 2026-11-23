package dev.viko.sessao.data.letterboxd

import android.content.Context
import android.net.Uri
import dev.viko.sessao.data.db.AppDatabase
import dev.viko.sessao.data.repo.TitleRepository
import dev.viko.sessao.data.repo.now
import dev.viko.sessao.data.settings.SettingsRepository
import dev.viko.sessao.domain.LetterboxdSeen
import dev.viko.sessao.domain.ListEntry
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.ScoreSource
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.domain.score.Scores
import dev.viko.sessao.data.api.toTitle
import dev.viko.sessao.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

class LetterboxdError(message: String) : Exception(message)

data class ImportResult(val imported: Int, val notFound: List<String>)

/**
 * Integração com o Letterboxd usando só o que é público: o feed RSS do perfil e os arquivos
 * de exportação/importação. Nada de login nem leitura das páginas do site.
 */
class LetterboxdRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val titles: TitleRepository,
    private val settings: SettingsRepository,
    private val http: OkHttpClient,
) {
    /** Lê o feed e aplica as entradas novas. Retorna quantas entraram. */
    suspend fun syncFeed(): Int {
        val user = settings.current().letterboxdUser.trim()
        if (user.isEmpty()) throw LetterboxdError(t("Coloque o nome de usuário do Letterboxd nas Configurações."))
        val xml = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(LetterboxdFeed.url(user)).header("User-Agent", "Sessao-Android").build()
            try {
                http.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) throw LetterboxdError(t("Não foi possível ler o perfil do Letterboxd. Confira o nome de usuário."))
                    res.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                throw LetterboxdError(t("Sem conexão. Isso precisa de internet."))
            }
        }
        val parsed = runCatching { LetterboxdFeed.parse(xml) }.getOrElse {
            throw LetterboxdError(t("Não foi possível ler o perfil do Letterboxd. Confira o nome de usuário."))
        }
        val fresh = LetterboxdFeed.unseen(parsed, db.misc().seenGuids().toSet())
        // Do mais antigo para o mais novo: a nota que fica é a da entrada mais recente.
        fresh.sortedBy { it.publishedAt }.forEach { e ->
            ensureMovie(e.tmdbId)
            val cur = db.entries().get(MediaType.MOVIE, e.tmdbId)
            db.entries().upsert(LetterboxdFeed.apply(cur, e, now()))
        }
        db.misc().markSeen(fresh.map { LetterboxdSeen(it.guid, now()) })
        settings.setLetterboxdLastSync(now())
        return fresh.size
    }

    private suspend fun ensureMovie(id: Int) {
        if (db.titles().get(MediaType.MOVIE, id) != null) return
        runCatching { titles.refresh(TitleKey(MediaType.MOVIE, id)) }
    }

    /**
     * Importa o .zip exportado pelo Letterboxd. Os arquivos não têm o id do TMDB, então cada filme
     * é buscado por título e ano; os que não aparecem voltam na lista [ImportResult.notFound].
     */
    suspend fun importZip(uri: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportResult {
        val films = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { LetterboxdExportZip.read(it) }
        } ?: throw LetterboxdError(t("Não foi possível abrir o arquivo."))
        if (films.isEmpty()) throw LetterboxdError(t("O arquivo não tem os dados do Letterboxd (diary.csv, ratings.csv, watched.csv ou watchlist.csv)."))
        val notFound = mutableListOf<String>()
        var done = 0
        films.forEachIndexed { i, f ->
            onProgress(i + 1, films.size)
            val hit = runCatching { titles.searchMovie(f.name, f.year) }.getOrNull()
            if (hit == null) { notFound += f.name + (f.year?.let { " ($it)" } ?: ""); return@forEachIndexed }
            if (db.titles().get(MediaType.MOVIE, hit.key.id) == null) db.titles().upsert(hit.toTitle())
            val t = now()
            val cur = db.entries().get(MediaType.MOVIE, hit.key.id)
            val lastDate = f.watchedDates.lastOrNull()?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            var e = cur ?: ListEntry(type = MediaType.MOVIE, id = hit.key.id, status = WatchStatus.PLANNING, addedAt = t, updatedAt = t)
            if (f.watched) {
                e = e.copy(
                    status = WatchStatus.COMPLETED,
                    timesWatched = maxOf(e.timesWatched, f.watchedDates.size, 1),
                    startedAt = e.startedAt ?: f.watchedDates.firstOrNull()?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: lastDate,
                    finishedAt = maxOf(e.finishedAt ?: 0L, lastDate ?: 0L).takeIf { it > 0 } ?: e.finishedAt,
                )
            } else if (f.watchlist && cur == null) {
                e = e.copy(status = WatchStatus.PLANNING)
            }
            if (f.rating != null && (cur == null || cur.scoreSource == ScoreSource.LETTERBOXD || cur.scoreRaw == 0)) {
                e = e.copy(scoreRaw = Scores.fromLetterboxd(f.rating), scoreSource = ScoreSource.LETTERBOXD, scoreUpdatedAt = t)
            }
            // A importação não conta como mudança feita no app (não volta na próxima exportação).
            db.entries().upsert(e.copy(updatedAt = cur?.updatedAt ?: 0L))
            done++
        }
        return ImportResult(done, notFound)
    }

    /** Gera o CSV para letterboxd.com/import. Sem [all], só o que mudou desde a última exportação. */
    suspend fun export(uri: Uri, all: Boolean): Int {
        val s = settings.current()
        val chosen = LetterboxdCsv.select(db.entries().all(), s.letterboxdLastExport, all)
        val rows = chosen.mapNotNull { e ->
            val title = db.titles().get(MediaType.MOVIE, e.id) ?: return@mapNotNull null
            ExportRow(
                tmdbId = e.id, title = title.name, year = title.year, raw = e.scoreRaw,
                watchedDate = e.finishedAt?.takeIf { e.status == WatchStatus.COMPLETED }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() },
                rewatch = e.timesWatched > 1,
            )
        }
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(LetterboxdCsv.build(rows).toByteArray()) }
                ?: throw LetterboxdError(t("Não foi possível salvar o arquivo."))
        }
        settings.setLetterboxdLastExport(now())
        return rows.size
    }
}
