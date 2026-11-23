package dev.viko.sessao.domain.score

import dev.viko.sessao.i18n.t
import kotlinx.serialization.Serializable

@Serializable
enum class ScoreSystem(val label: String) {
    LETTERS(t("Letras")),
    STARS_5(t("5 estrelas")),
    SMILEY_7(t("Carinhas")),
    POINT_10("0–10"),
    POINT_10_DECIMAL(t("0–10 com decimal")),
    POINT_100("0–100");

    companion object {
        fun parse(name: String?): ScoreSystem? = entries.firstOrNull { it.name == name }
    }
}

/** Como uma nota aparece na tela. */
sealed interface ScoreDisplay {
    data object None : ScoreDisplay
    data class Text(val text: String) : ScoreDisplay

    /** Estrelas em meias unidades: 7 = 3,5 estrelas. */
    data class Stars(val halves: Int) : ScoreDisplay

    /** [level] de 1 (Detestei) a 7 (Adorei). */
    data class Face(val level: Int, val label: String) : ScoreDisplay
}
