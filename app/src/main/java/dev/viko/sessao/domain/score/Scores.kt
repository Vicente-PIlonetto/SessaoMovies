package dev.viko.sessao.domain.score

import kotlin.math.roundToInt

/**
 * Conversão entre o valor guardado (0–100) e cada sistema de nota.
 * 0 sempre significa "sem nota".
 */
object Scores {
    fun display(raw: Int, system: ScoreSystem): ScoreDisplay {
        if (raw <= 0) return ScoreDisplay.None
        val r = raw.coerceAtMost(100)
        return when (system) {
            ScoreSystem.POINT_100 -> ScoreDisplay.Text("$r")
            ScoreSystem.POINT_10_DECIMAL -> ScoreDisplay.Text(decimal(r))
            ScoreSystem.POINT_10 -> ScoreDisplay.Text("${point10(r)}")
            ScoreSystem.STARS_5 -> ScoreDisplay.Stars(starHalves(r))
            ScoreSystem.SMILEY_7 -> Bands.of(r)!!.let { ScoreDisplay.Face(it.value, it.face) }
            ScoreSystem.LETTERS -> ScoreDisplay.Text(LetterGrade.nearest(r).label)
        }
    }

    /** Versão em texto (sistema secundário, acessibilidade, notificações). */
    fun text(raw: Int, system: ScoreSystem): String = when (val d = display(raw, system)) {
        ScoreDisplay.None -> "–"
        is ScoreDisplay.Text -> d.text
        is ScoreDisplay.Stars -> starsText(d.halves)
        is ScoreDisplay.Face -> d.label
    }

    fun decimal(raw: Int): String = "${raw / 10},${raw % 10}"

    fun point10(raw: Int): Int = (raw / 10.0).roundHalfUp().coerceIn(1, 10)

    fun starHalves(raw: Int): Int = (raw / 10.0).roundHalfUp().coerceIn(1, 10)

    fun starsText(halves: Int): String = if (halves % 2 == 0) "${halves / 2}" else "${halves / 2},5"

    /**
     * Valor que o sistema grava ao escolher a opção mais próxima.
     * Ex.: 72 em letras vira 71 (B−−); 72 em 5 estrelas vira 70 (3,5).
     */
    fun snap(raw: Int, system: ScoreSystem): Int {
        if (raw <= 0) return 0
        val r = raw.coerceAtMost(100)
        return when (system) {
            ScoreSystem.POINT_100, ScoreSystem.POINT_10_DECIMAL -> r
            ScoreSystem.POINT_10 -> point10(r) * 10
            ScoreSystem.STARS_5 -> starHalves(r) * 10
            ScoreSystem.SMILEY_7 -> Bands.of(r)!!.mid
            ScoreSystem.LETTERS -> LetterGrade.nearest(r).raw
        }
    }

    /** Nota do Letterboxd (0,5 a 5 estrelas) para 0–100: multiplica por 20. */
    fun fromLetterboxd(stars: Double): Int = (stars * 20).roundToInt().coerceIn(0, 100)

    /** 0–100 para estrelas do Letterboxd, arredondando para a meia estrela mais próxima (0,5 a 5). */
    fun toLetterboxd(raw: Int): Double? {
        if (raw <= 0) return null
        val halves = (raw / 10.0).roundHalfUp().coerceIn(1, 10)
        return halves / 2.0
    }

    private fun Double.roundHalfUp(): Int = (this + 1e-9).roundToInt()
}
