package dev.viko.sessao.data.backup

import android.content.Context
import android.net.Uri
import dev.viko.sessao.data.db.AppDatabase
import dev.viko.sessao.data.repo.now
import dev.viko.sessao.data.settings.Settings
import dev.viko.sessao.data.settings.SettingsRepository
import dev.viko.sessao.domain.ListEntry
import dev.viko.sessao.domain.Title
import dev.viko.sessao.domain.WatchedEpisode
import dev.viko.sessao.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tudo que é meu: itens, episódios marcados (todas as rodadas), notas, configurações e Letterboxd.
 * Os títulos vão só com o básico para a lista abrir na hora; o resto é baixado do TMDB de novo.
 */
@Serializable
data class Backup(
    val version: Int = 1,
    val createdAt: Long,
    val entries: List<ListEntry>,
    val watched: List<WatchedEpisode>,
    val titles: List<Title>,
    val settings: Settings,
)

class BackupManager(private val context: Context, private val db: AppDatabase, private val settings: SettingsRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val entries = db.entries().all()
        val keys = entries.map { it.key }.toSet()
        val titles = db.titles().all().filter { it.key in keys }.map { basic(it) }
        val b = Backup(createdAt = now(), entries = entries, watched = db.watched().all(), titles = titles, settings = settings.current().copy(tmdbKey = ""))
        context.contentResolver.openOutputStream(uri)?.use { it.write(json.encodeToString(Backup.serializer(), b).toByteArray()) }
            ?: error(t("Não foi possível salvar o arquivo."))
    }

    /** Restaura e retorna quantos itens entraram. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error(t("Não foi possível abrir o arquivo."))
        val b = json.decodeFromString(Backup.serializer(), text)
        b.titles.forEach { t -> if (db.titles().get(t.type, t.id) == null) db.titles().upsert(t.copy(updatedAt = 0)) }
        b.entries.forEach { db.entries().upsert(it) }
        if (b.watched.isNotEmpty()) db.watched().upsert(b.watched)
        settings.restore(b.settings)
        b.entries.size
    }

    private fun basic(t: Title) = Title(
        type = t.type, id = t.id, name = t.name, originalName = t.originalName, year = t.year,
        posterPath = t.posterPath, backdropPath = t.backdropPath, posterColor = t.posterColor, seasons = t.seasons,
    )
}
