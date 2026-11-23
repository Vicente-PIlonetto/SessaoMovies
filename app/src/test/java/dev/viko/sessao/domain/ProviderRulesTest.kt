package dev.viko.sessao.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRulesTest {
    private val netflix = Provider(8, "Netflix", null, 1)
    private val max = Provider(1899, "Max", null, 5)
    private val globo = Provider(307, "Globoplay", null, 3)
    private val apple = Provider(2, "Apple TV", null, 2)

    private val wp = WatchProviders(
        offers = listOf(
            Offer(OfferKind.BUY, apple), Offer(OfferKind.FLATRATE, netflix), Offer(OfferKind.FLATRATE, max),
            Offer(OfferKind.FLATRATE, globo), Offer(OfferKind.RENT, apple), Offer(OfferKind.FREE, globo),
        ),
    )

    @Test fun groupsInFixedOrder() {
        assertEquals(listOf(OfferKind.FLATRATE, OfferKind.FREE, OfferKind.RENT, OfferKind.BUY), ProviderRules.grouped(wp, emptySet()).map { it.first })
    }

    @Test fun myServicesComeFirst() {
        val sub = ProviderRules.grouped(wp, setOf(1899)).first().second
        assertEquals(listOf(max, netflix, globo), sub)
        val noMine = ProviderRules.grouped(wp, emptySet()).first().second
        assertEquals(listOf(netflix, globo, max), noMine)
    }

    @Test fun newInMyServices() {
        val before = WatchProviders(offers = listOf(Offer(OfferKind.FLATRATE, netflix)))
        assertEquals(listOf(max), ProviderRules.newInMyServices(before, wp, setOf(1899, 8)))
        // Primeira leitura (sem dados anteriores) não avisa.
        assertTrue(ProviderRules.newInMyServices(null, wp, setOf(1899)).isEmpty())
        // Aluguel não conta como "entrou no serviço".
        assertTrue(ProviderRules.newInMyServices(before, wp, setOf(2)).isEmpty())
    }
}
