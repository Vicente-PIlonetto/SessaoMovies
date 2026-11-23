package dev.viko.sessao.domain

/** "Onde assistir": agrupar, ordenar pelos meus serviços e achar o que entrou num serviço meu. */
object ProviderRules {
    /**
     * Grupos na ordem Assinatura, Grátis, Aluguel, Compra. Em cada grupo, os meus serviços vêm primeiro,
     * depois a ordem de destaque do TMDB e o nome. Um serviço aparece uma vez por grupo.
     */
    fun grouped(wp: WatchProviders?, mine: Set<Int>): List<Pair<OfferKind, List<Provider>>> {
        val offers = wp?.offers.orEmpty()
        return OfferKind.entries.sortedBy { it.order }.mapNotNull { kind ->
            val list = offers.filter { it.kind == kind }.map { it.provider }.distinctBy { it.id }
                .sortedWith(compareByDescending<Provider> { it.id in mine }.thenBy { it.priority }.thenBy { it.name.lowercase() })
            if (list.isEmpty()) null else kind to list
        }
    }

    /** Serviços em que dá para ver sem pagar à parte (assinatura ou grátis). */
    fun streamable(wp: WatchProviders?): Set<Int> =
        wp?.offers.orEmpty().filter { it.kind == OfferKind.FLATRATE || it.kind == OfferKind.FREE }.map { it.provider.id }.toSet()

    /** Disponível em algum serviço meu (assinatura ou grátis)? */
    fun inMyServices(wp: WatchProviders?, mine: Set<Int>): Boolean = streamable(wp).any { it in mine }

    /**
     * Serviços meus que passaram a ter o título (para o aviso "agora na Max").
     * Sem dados anteriores, não avisa nada: a primeira leitura só registra.
     */
    fun newInMyServices(before: WatchProviders?, after: WatchProviders?, mine: Set<Int>): List<Provider> {
        if (before == null || after == null) return emptyList()
        val had = streamable(before)
        return after.offers.filter { (it.kind == OfferKind.FLATRATE || it.kind == OfferKind.FREE) && it.provider.id in mine && it.provider.id !in had }
            .map { it.provider }.distinctBy { it.id }
    }
}
