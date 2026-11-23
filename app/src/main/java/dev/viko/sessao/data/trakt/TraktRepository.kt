package dev.viko.sessao.data.trakt

import android.content.Context
import dev.viko.sessao.data.db.AppDatabase
import dev.viko.sessao.data.repo.ListRepository
import dev.viko.sessao.data.repo.TitleRepository
import dev.viko.sessao.data.repo.now
import dev.viko.sessao.domain.Ep
import dev.viko.sessao.domain.ListEntry
import dev.viko.sessao.domain.MediaType
import dev.viko.sessao.domain.TitleKey
import dev.viko.sessao.domain.WatchStatus
import dev.viko.sessao.domain.WatchedEpisode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Resultado de uma sincronização, para a tela. */
data class TraktSyncResult(val pulled: Int, val pushed: Int, val warning: String? = null)

/**
 * Conta do Trakt: login por código no aparelho e sincronização nos dois sentidos
 * (episódios e filmes vistos, notas e lista Quero ver).
 */
class TraktRepository(
    context: Context,
    private val db: AppDatabase,
    private val lists: ListRepository,
    private val titles: TitleRepository,
    clientId: String,
) {
    private val store = TraktTokenStore(context)
    private val prefs = context.getSharedPreferences("trakt_sync", Context.MODE_PRIVATE)
    val client = TraktClient(clientId) { store.load()?.accessToken }
    private val mutex = Mutex()

    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<TraktSession?> = _session

    val available: Boolean get() = client.clientId.isNotBlank()
    val lastSync: Long get() = prefs.getLong("last_sync", 0)

    // ---------- Login ----------

    suspend fun startLogin(): DeviceCode = client.call { deviceCode(mapOf("client_id" to client.clientId)) }

    /**
     * Espera a pessoa aprovar no site. Retorna true quando entrou; false se o código venceu ou foi negado.
     * O Trakt responde 400 enquanto espera e 429 se a consulta for rápida demais.
     */
    suspend fun waitForApproval(code: DeviceCode): Boolean {
        val until = now() + code.expiresIn * 1000L
        var interval = code.interval.coerceAtLeast(1) * 1000L
        while (now() < until) {
            delay(interval)
            val res = client.call { deviceToken(DeviceTokenRequest(code.deviceCode, client.clientId)) }
            when (res.code()) {
                200 -> {
                    val t = res.body() ?: return false
                    store.save(TraktSession(t.accessToken, t.refreshToken, (t.createdAt + t.expiresIn) * 1000))
                    runCatching { client.call { me() } }.getOrNull()?.let { u ->
                        store.save(store.load()!!.copy(username = u.username, name = u.name, avatar = u.images?.avatar?.full, slug = u.ids.slug.orEmpty()))
                    }
                    _session.value = store.load()
                    // Primeira sincronização manda tudo o que já está no app.
                    prefs.edit().putLong("last_sync", 0).putLong("last_pull", 0).apply()
                    return true
                }
                400 -> {}
                429 -> interval += 1000
                else -> return false // 404 código inválido, 409 já usado, 410 vencido, 418 negado
            }
        }
        return false
    }

    fun logout() {
        store.clear()
        prefs.edit().clear().apply()
        _session.value = null
    }

    /** Renova o token quando falta menos de um dia. O refresh token só vale uma vez. */
    private suspend fun ensureFresh() {
        val s = store.load() ?: throw TraktError.LoggedOut()
        if (s.expiresAt - now() > 24 * 3600 * 1000L) return
        val t = try {
            client.call { refresh(RefreshRequest(s.refreshToken, client.clientId)) }
        } catch (e: TraktError.Other) {
            logout(); throw TraktError.LoggedOut()
        }
        store.save(s.copy(accessToken = t.accessToken, refreshToken = t.refreshToken, expiresAt = (t.createdAt + t.expiresIn) * 1000))
        _session.value = store.load()
    }

    /** Atualiza nome, foto e slug da conta (o perfil pode mudar no site). */
    suspend fun refreshProfile() {
        val s = store.load() ?: return
        val u = runCatching { client.call { me() } }.getOrNull() ?: return
        store.save(s.copy(username = u.username, name = u.name, avatar = u.images?.avatar?.full, slug = u.ids.slug.orEmpty()))
        _session.value = store.load()
    }

    // ---------- Sincronização ----------

    suspend fun sync(): TraktSyncResult = mutex.withLock {
        if (store.load() == null) throw TraktError.LoggedOut()
        ensureFresh()
        refreshProfile()
        val started = now()
        val since = lastSync
        val pulledKeys = mutableSetOf<TitleKey>()
        val pulledEps = mutableSetOf<Pair<Int, Ep>>()
        var pulled = 0

        // 1. Trazer do Trakt.
        pulled += pullHistory(pulledKeys, pulledEps)
        pulled += pullMovies(pulledKeys)
        pulled += pullRatings(pulledKeys)
        pulled += pullWatchlist(pulledKeys)

        // 2. Mandar o que mudou aqui (sem reenviar o que acabou de chegar).
        val plan = TraktRules.push(db.entries().all(), db.watched().all(), since, pulledKeys, pulledEps)
        var pushed = 0
        var warning: String? = null
        if (!plan.isEmpty) {
            for ((body, send) in listOf<Pair<SyncBody, suspend (SyncBody) -> retrofit2.Response<Unit>>>(
                plan.history to { b -> client.call { addHistory(b) } },
                plan.ratings to { b -> client.call { addRatings(b) } },
                plan.watchlist to { b -> client.call { addWatchlist(b) } },
            )) {
                if (body.movies.isEmpty() && body.shows.isEmpty()) continue
                val r = send(body)
                if (r.code() == 420) warning = TraktError.AccountLimit().message
                else if (!r.isSuccessful) throw TraktError.Other(r.code())
                else pushed += body.movies.size + body.shows.size
            }
        }
        prefs.edit().putLong("last_sync", started).apply()
        TraktSyncResult(pulled, pushed, warning)
    }

    /** Episódios e filmes vistos desde a última leitura (histórico paginado). */
    private suspend fun pullHistory(keys: MutableSet<TitleKey>, eps: MutableSet<Pair<Int, Ep>>): Int {
        val lastPull = prefs.getLong("last_pull", 0)
        val startAt = lastPull.takeIf { it > 0 }?.let { TraktRules.formatTime(it - 60_000) }
        val all = mutableListOf<HistoryItem>()
        var page = 1
        while (true) {
            val r = client.call { history(startAt, page) }
            if (!r.isSuccessful) throw TraktError.Other(r.code())
            all += r.body().orEmpty()
            val pages = r.headers()["X-Pagination-Page-Count"]?.toIntOrNull() ?: 1
            if (page >= pages || page >= 200) break
            page++
        }
        var count = 0
        TraktRules.episodesByShow(all).forEach { (showId, list) ->
            val key = TitleKey(MediaType.TV, showId)
            if (!ensureTitle(key)) return@forEach
            val entry = db.entries().get(key.type, key.id) ?: ListEntry(
                type = MediaType.TV, id = showId, status = WatchStatus.WATCHING, addedAt = now(),
                startedAt = list.minOf { it.second }, updatedAt = now(),
            ).also { db.entries().upsert(it) }
            val have = db.watched().get(showId, entry.cycle).map { it.ep }.toSet()
            val fresh = list.filter { it.first !in have }.distinctBy { it.first }
            if (fresh.isNotEmpty()) {
                db.watched().upsert(fresh.map { (ep, at) -> WatchedEpisode(showId, ep.season, ep.episode, entry.cycle, at) })
                lists.recompute(key)
                count += fresh.size
            }
            fresh.forEach { eps += showId to it.first }
            keys += key
        }
        prefs.edit().putLong("last_pull", now()).apply()
        return count
    }

    /** Filmes vistos: vira Visto, com as vezes e a data da última vez. */
    private suspend fun pullMovies(keys: MutableSet<TitleKey>): Int {
        var count = 0
        client.call { watchedMovies() }.forEach { w ->
            val id = w.movie.ids.tmdb ?: return@forEach
            val key = TitleKey(MediaType.MOVIE, id)
            val at = TraktRules.parseTime(w.lastWatchedAt) ?: now()
            val cur = db.entries().get(key.type, key.id)
            if (cur != null && cur.status == WatchStatus.COMPLETED && cur.timesWatched >= w.plays && (cur.finishedAt ?: 0) >= at) return@forEach
            if (!ensureTitle(key)) return@forEach
            val base = cur ?: ListEntry(type = MediaType.MOVIE, id = id, status = WatchStatus.COMPLETED, addedAt = now(), updatedAt = now())
            db.entries().upsert(base.copy(
                status = WatchStatus.COMPLETED,
                timesWatched = maxOf(base.timesWatched, w.plays, 1),
                startedAt = base.startedAt ?: at,
                finishedAt = maxOf(base.finishedAt ?: 0, at),
                updatedAt = now(),
            ))
            keys += key
            count++
        }
        return count
    }

    private suspend fun pullRatings(keys: MutableSet<TitleKey>): Int {
        var count = 0
        for ((path, type) in listOf("movies" to MediaType.MOVIE, "shows" to MediaType.TV)) {
            client.call { ratings(path) }.forEach { r ->
                val id = (if (type == MediaType.MOVIE) r.movie?.ids?.tmdb else r.show?.ids?.tmdb) ?: return@forEach
                val cur = db.entries().get(type, id) ?: return@forEach
                val at = TraktRules.parseTime(r.ratedAt) ?: return@forEach
                val out = TraktRules.applyRating(cur, r.rating, at)
                if (out != cur) { db.entries().upsert(out); keys += cur.key; count++ }
            }
        }
        return count
    }

    /** Lista Quero ver do Trakt: o que não está na lista entra como Quero ver. */
    private suspend fun pullWatchlist(keys: MutableSet<TitleKey>): Int {
        var count = 0
        client.call { watchlist() }.forEach { w ->
            val (type, id) = when {
                w.movie?.ids?.tmdb != null -> MediaType.MOVIE to w.movie.ids.tmdb
                w.show?.ids?.tmdb != null -> MediaType.TV to w.show.ids.tmdb
                else -> return@forEach
            }
            val key = TitleKey(type, id)
            if (db.entries().get(type, id) != null) { keys += key; return@forEach }
            if (!ensureTitle(key)) return@forEach
            val at = TraktRules.parseTime(w.listedAt) ?: now()
            db.entries().upsert(ListEntry(type = type, id = id, status = WatchStatus.PLANNING, addedAt = at, updatedAt = now()))
            keys += key
            count++
        }
        return count
    }

    /** Garante os dados do TMDB do título (para a lista abrir offline). */
    private suspend fun ensureTitle(key: TitleKey): Boolean {
        if (db.titles().get(key.type, key.id)?.let { it.updatedAt > 0 } == true) return true
        return runCatching { titles.refresh(key) }.isSuccess
    }
}
