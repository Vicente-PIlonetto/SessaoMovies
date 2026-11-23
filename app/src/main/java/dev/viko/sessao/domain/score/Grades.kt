package dev.viko.sessao.domain.score

/** Faixas base usadas por 0–7 e pelas 7 carinhas. */
object Bands {
    data class Band(val value: Int, val min: Int, val max: Int, val mid: Int, val face: String)

    val all = listOf(
        Band(7, 95, 100, 97, "Adorei"),
        Band(6, 85, 94, 89, "Gostei muito"),
        Band(5, 70, 84, 77, "Gostei"),
        Band(4, 55, 69, 62, "Ok"),
        Band(3, 40, 54, 47, "Não gostei"),
        Band(2, 20, 39, 29, "Ruim"),
        Band(1, 1, 19, 10, "Detestei"),
    )

    /** 0 = sem nota. */
    fun of(raw: Int): Band? = if (raw <= 0) null else all.first { raw in it.min..it.max || (raw > 100 && it.value == 7) }

    fun byValue(value: Int): Band? = all.firstOrNull { it.value == value }
}

/**
 * Letras simples, sem + e −: cada uma grava um valor fixo (0–10: F 2, E 4, D 5, C 6, B 7, A 9, S 10).
 * Notas de outra escala caem na letra mais próxima pelos limites [min].
 */
enum class Letter(val raw: Int, val min: Int) {
    F(20, 1), E(40, 30), D(50, 45), C(60, 55), B(70, 65), A(90, 80), S(100, 95);

    companion object {
        /** Letra de um valor 0–100 (0 = sem nota). */
        fun of(raw: Int): Letter? = if (raw <= 0) null else entries.last { raw.coerceAtMost(100) >= it.min }
    }
}
