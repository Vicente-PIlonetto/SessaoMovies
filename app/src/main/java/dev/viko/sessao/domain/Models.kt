package dev.viko.sessao.domain

import dev.viko.sessao.i18n.t
import kotlinx.serialization.Serializable

/** Filme ou série. Os ids do TMDB se repetem entre os dois, então o tipo faz parte da chave. */
@Serializable
enum class MediaType(val path: String) {
    MOVIE("movie"), TV("tv");

    val label: String get() = if (this == MOVIE) t("Filme") else t("Série")
    val plural: String get() = if (this == MOVIE) t("Filmes") else t("Séries")

    companion object {
        fun fromPath(p: String?): MediaType? = entries.firstOrNull { it.path == p }
    }
}

/** Chave de um título: tipo + id do TMDB. */
@Serializable
data class TitleKey(val type: MediaType, val id: Int) {
    override fun toString() = "${type.path}:$id"

    companion object {
        fun parse(s: String): TitleKey? {
            val (p, i) = s.split(':').takeIf { it.size == 2 } ?: return null
            return TitleKey(MediaType.fromPath(p) ?: return null, i.toIntOrNull() ?: return null)
        }
    }
}

/**
 * Status de um item da lista.
 * Filmes usam só Quero ver, Visto (COMPLETED) e Abandonado.
 */
@Serializable
enum class WatchStatus {
    PLANNING, WATCHING, CAUGHT_UP, PAUSED, COMPLETED, REWATCHING, DROPPED;

    fun label(type: MediaType): String = when (this) {
        PLANNING -> t("Quero ver")
        WATCHING -> t("Vendo")
        CAUGHT_UP -> t("Em dia")
        PAUSED -> t("Pausado")
        COMPLETED -> if (type == MediaType.MOVIE) t("Visto") else t("Terminado")
        REWATCHING -> t("Revendo")
        DROPPED -> t("Abandonado")
    }

    /** Conta como "em andamento" (atualização diária, avisos de episódio). */
    val active: Boolean get() = this == WATCHING || this == CAUGHT_UP || this == PAUSED || this == REWATCHING

    companion object {
        fun parse(name: String?): WatchStatus? = entries.firstOrNull { it.name == name }

        fun forType(type: MediaType): List<WatchStatus> =
            if (type == MediaType.MOVIE) listOf(PLANNING, COMPLETED, DROPPED)
            else listOf(PLANNING, WATCHING, CAUGHT_UP, PAUSED, COMPLETED, REWATCHING, DROPPED)

        /** Ordem padrão das categorias no Início e na lista completa. */
        fun defaultOrder(type: MediaType): List<WatchStatus> =
            if (type == MediaType.MOVIE) listOf(PLANNING, COMPLETED, DROPPED)
            else listOf(WATCHING, CAUGHT_UP, PLANNING, PAUSED, REWATCHING, COMPLETED, DROPPED)
    }
}

/** De onde veio a nota. */
@Serializable
enum class ScoreSource { APP, LETTERBOXD }

/** Um episódio, por temporada e número. Temporada 0 = especiais. */
@Serializable
data class Ep(val season: Int, val episode: Int) : Comparable<Ep> {
    override fun compareTo(other: Ep): Int = compareValuesBy(this, other, { it.season }, { it.episode })
    val label: String get() = "T$season · E$episode"
}

/** Grupo de provedor em "Onde assistir". */
@Serializable
enum class OfferKind(val order: Int) {
    FLATRATE(0), FREE(1), RENT(2), BUY(3);

    val label: String get() = when (this) {
        FLATRATE -> t("Assinatura")
        FREE -> t("Grátis")
        RENT -> t("Aluguel")
        BUY -> t("Compra")
    }
}

@Serializable
data class Provider(val id: Int, val name: String, val logoPath: String?, val priority: Int = 999)

@Serializable
data class Offer(val kind: OfferKind, val provider: Provider)

/** "Onde assistir" de um título no Brasil. [link] é a página do TMDB/JustWatch. */
@Serializable
data class WatchProviders(val link: String? = null, val offers: List<Offer> = emptyList())

/** Temporada com a contagem de episódios do TMDB. */
@Serializable
data class SeasonInfo(
    val number: Int,
    val episodeCount: Int,
    val name: String = "",
    val airDate: String? = null,
    val posterPath: String? = null,
)

/** Episódio anunciado ou o último que saiu. [airDate] no formato do TMDB (aaaa-mm-dd). */
@Serializable
data class AiredEpisode(val season: Int, val episode: Int, val airDate: String?, val name: String? = null) {
    val ep: Ep get() = Ep(season, episode)
}
