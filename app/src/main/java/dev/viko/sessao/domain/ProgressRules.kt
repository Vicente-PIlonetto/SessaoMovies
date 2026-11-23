package dev.viko.sessao.domain

/**
 * Progresso de séries a partir dos episódios marcados.
 * A temporada 0 (especiais) pode ser marcada, mas fica fora de todas as contagens.
 */
object Progress {
    fun mainSeasons(seasons: List<SeasonInfo>): List<SeasonInfo> =
        seasons.filter { it.number >= 1 && it.episodeCount > 0 }.sortedBy { it.number }

    fun totalEpisodes(seasons: List<SeasonInfo>): Int = mainSeasons(seasons).sumOf { it.episodeCount }

    private fun inMain(e: Ep, seasons: List<SeasonInfo>): Boolean =
        e.season >= 1 && mainSeasons(seasons).any { it.number == e.season && e.episode in 1..it.episodeCount }

    fun watchedMain(watched: Collection<Ep>, seasons: List<SeasonInfo>): Int = watched.count { inMain(it, seasons) }

    /** Último episódio marcado (fora os especiais). */
    fun last(watched: Collection<Ep>, seasons: List<SeasonInfo>): Ep? = watched.filter { inMain(it, seasons) }.maxOrNull()

    /** Episódio seguinte a [e] na ordem da série, virando a temporada quando ela acaba. */
    fun after(e: Ep, seasons: List<SeasonInfo>): Ep? {
        val main = mainSeasons(seasons)
        val cur = main.firstOrNull { it.number == e.season }
        if (cur != null && e.episode < cur.episodeCount) return Ep(e.season, e.episode + 1)
        val nextSeason = main.firstOrNull { it.number > e.season } ?: return null
        return Ep(nextSeason.number, 1)
    }

    /** O que o "+1" marca: o episódio depois do último marcado (ou o primeiro da série). */
    fun next(watched: Collection<Ep>, seasons: List<SeasonInfo>): Ep? {
        val last = last(watched, seasons)
        return if (last == null) mainSeasons(seasons).firstOrNull()?.let { Ep(it.number, 1) } else after(last, seasons)
    }

    /** Já saiu? Sem data do último episódio, só conta como lançado se a série acabou. */
    fun isAired(e: Ep, lastAired: Ep?, ended: Boolean): Boolean = when {
        lastAired != null -> e <= lastAired
        else -> ended
    }

    /** Todos os episódios da série (fora especiais), em ordem. */
    fun allEpisodes(seasons: List<SeasonInfo>): List<Ep> =
        mainSeasons(seasons).flatMap { s -> (1..s.episodeCount).map { Ep(s.number, it) } }

    fun airedEpisodes(seasons: List<SeasonInfo>, lastAired: Ep?, ended: Boolean): List<Ep> =
        allEpisodes(seasons).filter { isAired(it, lastAired, ended) }

    /** Episódios lançados que ainda não marquei. */
    fun remaining(watched: Collection<Ep>, seasons: List<SeasonInfo>, lastAired: Ep?, ended: Boolean): Int {
        val set = watched.toSet()
        return airedEpisodes(seasons, lastAired, ended).count { it !in set }
    }

    /** Episódios até [target] (inclusive), para "marcar até aqui". Especiais marcam só eles mesmos. */
    fun upTo(target: Ep, seasons: List<SeasonInfo>): List<Ep> =
        if (target.season == 0) listOf(target) else allEpisodes(seasons).filter { it <= target }

    fun seasonEpisodes(season: SeasonInfo): List<Ep> = (1..season.episodeCount).map { Ep(season.number, it) }

    /** "T2 · E5" = último marcado; sem nada marcado, "T1 · E0". */
    fun progressText(watched: Collection<Ep>, seasons: List<SeasonInfo>): String {
        val l = last(watched, seasons) ?: return "T${mainSeasons(seasons).firstOrNull()?.number ?: 1} · E0"
        return l.label
    }
}

/** Mudanças automáticas de status de séries. */
object StatusRules {
    /**
     * Status depois de marcar ou desmarcar episódios.
     * - tudo marcado numa série encerrada: Terminado
     * - tudo que já saiu marcado numa série em produção: Em dia
     * - senão: Vendo (Revendo continua Revendo)
     */
    fun afterProgress(
        current: WatchStatus,
        watchedMain: Int,
        remaining: Int,
        total: Int,
        ended: Boolean,
    ): WatchStatus {
        if (watchedMain == 0) {
            // Desmarcou tudo: volta para Vendo se estava em dia ou terminado.
            return if (current == WatchStatus.CAUGHT_UP || current == WatchStatus.COMPLETED) WatchStatus.WATCHING else current
        }
        if (total > 0 && watchedMain >= total && ended) return WatchStatus.COMPLETED
        if (remaining == 0 && !ended) return WatchStatus.CAUGHT_UP
        return if (current == WatchStatus.REWATCHING) WatchStatus.REWATCHING else WatchStatus.WATCHING
    }

    /**
     * Depois de atualizar a série no TMDB: "Em dia" volta para "Vendo" quando sai episódio novo,
     * e vira "Terminado" se a série acabou sem episódios novos.
     */
    fun afterRefresh(current: WatchStatus, watchedMain: Int, remaining: Int, total: Int, ended: Boolean): WatchStatus = when {
        current != WatchStatus.CAUGHT_UP -> current
        remaining > 0 -> WatchStatus.WATCHING
        ended && total > 0 && watchedMain >= total -> WatchStatus.COMPLETED
        else -> current
    }
}

/** Regras sobre o item da lista (puras, para testar sem banco). */
object EntryRules {
    /**
     * Rever. Série: vira Revendo, soma 1 nas vezes vistas e começa uma rodada nova de episódios
     * (a anterior fica guardada). Filme: continua Visto, soma 1 e registra a data.
     */
    fun rewatch(e: ListEntry, now: Long): ListEntry = if (e.type == MediaType.TV) e.copy(
        status = WatchStatus.REWATCHING, timesWatched = e.timesWatched.coerceAtLeast(1) + 1,
        cycle = e.cycle + 1, startedAt = now, updatedAt = now,
    ) else e.copy(
        status = WatchStatus.COMPLETED, timesWatched = e.timesWatched.coerceAtLeast(1) + 1,
        finishedAt = now, updatedAt = now,
    )

    /** Troca o status preenchendo as datas; ao terminar, conta pelo menos uma vez vista. */
    fun withStatus(e: ListEntry, status: WatchStatus, now: Long): ListEntry {
        var out = e.copy(status = status, updatedAt = now, startedAt = e.startedAt ?: now)
        if (status == WatchStatus.COMPLETED && e.status != WatchStatus.COMPLETED) {
            out = out.copy(finishedAt = now, timesWatched = e.timesWatched.coerceAtLeast(1))
        }
        return out
    }
}
