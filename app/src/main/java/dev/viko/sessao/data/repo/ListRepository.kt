package dev.viko.sessao.data.repo

import dev.viko.sessao.data.db.AppDatabase
import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.EntryRules
import dev.viko.sessao.domain.Item
import dev.viko.sessao.domain.ListEntry
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.Progress
import dev.viko.sessao.domain.ScoreSource
import dev.viko.sessao.domain.StatusRules
import dev.viko.sessao.domain.Title
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.domain.WatchedEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

fun now() = System.currentTimeMillis()

/** A minha lista: itens, episódios marcados e as regras de status. Tudo local. */
class ListRepository(private val db: AppDatabase) {

    /** Itens com título e episódios da rodada atual. */
    val items: Flow<List<Item>> = combine(db.entries().observeAll(), db.titles().observeAll(), db.watched().observeAll()) { entries, titles, watched ->
        val byKey = titles.associateBy { it.key }
        val eps = watched.groupBy { it.showId to it.cycle }
        entries.map { e ->
            Item(e, byKey[e.key], if (e.type == MediaType.TV) eps[e.id to e.cycle].orEmpty().map { it.ep }.toSet() else emptySet())
        }
    }

    val statusMap: Flow<Map<TitleKey, WatchStatus>> = db.entries().observeAll().map { l -> l.associate { it.key to it.status } }

    fun item(key: TitleKey): Flow<Item?> = combine(
        db.entries().observe(key.type, key.id),
        db.titles().observe(key.type, key.id),
        db.watched().observeAll(),
    ) { e, t, w ->
        e?.let { entry -> Item(entry, t, if (key.type == MediaType.TV) w.filter { it.showId == key.id && it.cycle == entry.cycle }.map { it.ep }.toSet() else emptySet()) }
    }

    suspend fun get(key: TitleKey): ListEntry? = db.entries().get(key.type, key.id)

    /** Adiciona (ou muda o status de) um título. [title] garante o mínimo para a lista abrir offline. */
    suspend fun add(title: Title, status: WatchStatus) {
        if (db.titles().get(title.type, title.id) == null) db.titles().upsert(title)
        setStatus(title.key, status)
    }

    suspend fun remove(key: TitleKey) {
        db.entries().get(key.type, key.id)?.let { db.entries().delete(it) }
        if (key.type == MediaType.TV) db.watched().deleteShow(key.id)
    }

    /**
     * Muda o status escolhido na mão.
     * Série marcada como Terminado marca todos os episódios; filme Visto conta uma vez vista.
     */
    suspend fun setStatus(key: TitleKey, status: WatchStatus) {
        val t = now()
        val cur = db.entries().get(key.type, key.id)
        if (status == WatchStatus.REWATCHING && cur != null && key.type == MediaType.TV) { rewatch(key); return }
        var e = cur ?: ListEntry(type = key.type, id = key.id, status = status, addedAt = t, updatedAt = t)
        e = e.copy(status = status, updatedAt = t)
        when (status) {
            WatchStatus.WATCHING, WatchStatus.REWATCHING -> e = e.copy(startedAt = e.startedAt ?: t)
            WatchStatus.COMPLETED -> {
                e = e.copy(startedAt = e.startedAt ?: t, finishedAt = if (cur?.status == WatchStatus.COMPLETED) e.finishedAt ?: t else t, timesWatched = e.timesWatched.coerceAtLeast(1))
                if (key.type == MediaType.TV) {
                    val title = db.titles().get(key.type, key.id)
                    if (title != null) {
                        val all = Progress.allEpisodes(title.seasons)
                        db.watched().upsert(all.map { WatchedEpisode(key.id, it.season, it.episode, e.cycle, t) })
                    }
                }
            }
            else -> {}
        }
        db.entries().upsert(e)
    }

    suspend fun setScore(key: TitleKey, raw: Int) {
        val e = db.entries().get(key.type, key.id) ?: return
        val t = now()
        db.entries().upsert(e.copy(scoreRaw = raw.coerceIn(0, 100), scoreSource = ScoreSource.APP, scoreUpdatedAt = t, updatedAt = t))
    }

    suspend fun setNote(key: TitleKey, note: String) {
        val e = db.entries().get(key.type, key.id) ?: return
        db.entries().upsert(e.copy(note = note.take(500), updatedAt = now()))
    }

    suspend fun setDates(key: TitleKey, startedAt: Long?, finishedAt: Long?, timesWatched: Int) {
        val e = db.entries().get(key.type, key.id) ?: return
        db.entries().upsert(e.copy(startedAt = startedAt, finishedAt = finishedAt, timesWatched = timesWatched.coerceAtLeast(0), updatedAt = now()))
    }

    /** "+1": marca o próximo episódio, se já saiu. Retorna o episódio marcado. */
    suspend fun increment(key: TitleKey): Ep? {
        val e = db.entries().get(key.type, key.id) ?: return null
        val title = db.titles().get(key.type, key.id) ?: return null
        val watched = db.watched().get(key.id, e.cycle).map { it.ep }
        val next = Progress.next(watched, title.seasons) ?: return null
        if (!Progress.isAired(next, title.lastAired?.ep, title.ended)) return null
        db.watched().upsert(listOf(WatchedEpisode(key.id, next.season, next.episode, e.cycle, now())))
        recompute(key)
        return next
    }

    /** Marca até [target], inclusive, com os anteriores (toque longo no "+1"). */
    suspend fun markUpTo(key: TitleKey, target: Ep) {
        val e = ensureEntry(key)
        val title = db.titles().get(key.type, key.id) ?: return
        val t = now()
        db.watched().upsert(Progress.upTo(target, title.seasons).map { WatchedEpisode(key.id, it.season, it.episode, e.cycle, t) })
        recompute(key)
    }

    suspend fun setEpisode(key: TitleKey, ep: Ep, watched: Boolean) {
        val e = ensureEntry(key)
        if (watched) db.watched().upsert(listOf(WatchedEpisode(key.id, ep.season, ep.episode, e.cycle, now())))
        else db.watched().delete(key.id, e.cycle, ep.season, listOf(ep.episode))
        recompute(key)
    }

    suspend fun setSeason(key: TitleKey, season: Int, watched: Boolean) {
        val e = ensureEntry(key)
        val title = db.titles().get(key.type, key.id) ?: return
        val info = title.seasons.firstOrNull { it.number == season } ?: return
        val eps = Progress.seasonEpisodes(info)
        if (watched) {
            val t = now()
            db.watched().upsert(eps.map { WatchedEpisode(key.id, it.season, it.episode, e.cycle, t) })
        } else db.watched().delete(key.id, e.cycle, season, eps.map { it.episode })
        recompute(key)
    }

    /** Rever (veja [EntryRules.rewatch]). */
    suspend fun rewatch(key: TitleKey) {
        val e = db.entries().get(key.type, key.id) ?: return
        db.entries().upsert(EntryRules.rewatch(e, now()))
    }

    /** Entrada de uma série ao marcar episódio sem ter adicionado antes: entra como Vendo. */
    private suspend fun ensureEntry(key: TitleKey): ListEntry {
        db.entries().get(key.type, key.id)?.let { return it }
        val t = now()
        val e = ListEntry(type = key.type, id = key.id, status = WatchStatus.WATCHING, addedAt = t, startedAt = t, updatedAt = t)
        db.entries().upsert(e)
        return e
    }

    /** Aplica as regras automáticas depois de mudar episódios. */
    suspend fun recompute(key: TitleKey) {
        val e = db.entries().get(key.type, key.id) ?: return
        val title = db.titles().get(key.type, key.id) ?: return
        val watched = db.watched().get(key.id, e.cycle).map { it.ep }
        val main = Progress.watchedMain(watched, title.seasons)
        val remaining = Progress.remaining(watched, title.seasons, title.lastAired?.ep, title.ended)
        val status = StatusRules.afterProgress(e.status, main, remaining, title.totalEpisodes, title.ended)
        db.entries().upsert(withStatus(e, status))
    }

    /** Depois de atualizar no TMDB: "Em dia" volta para "Vendo" quando sai episódio novo. */
    suspend fun afterRefresh(key: TitleKey) {
        val e = db.entries().get(key.type, key.id) ?: return
        if (key.type != MediaType.TV) return
        val title = db.titles().get(key.type, key.id) ?: return
        val watched = db.watched().get(key.id, e.cycle).map { it.ep }
        val main = Progress.watchedMain(watched, title.seasons)
        val remaining = Progress.remaining(watched, title.seasons, title.lastAired?.ep, title.ended)
        val status = StatusRules.afterRefresh(e.status, main, remaining, title.totalEpisodes, title.ended)
        if (status != e.status) db.entries().upsert(withStatus(e, status))
    }

    private fun withStatus(e: ListEntry, status: WatchStatus): ListEntry = EntryRules.withStatus(e, status, now())
}
