package dev.viko.sessao.domain.score

import kotlin.math.sqrt

/** Distribuição de notas para as estatísticas, no sistema de nota escolhido. */
object ScoreBuckets {
    /** [order] ordena o eixo; [faceLevel] preenchido quando o eixo é de carinhas. */
    data class Bucket(val label: String, val order: Int, val faceLevel: Int? = null)

    fun axis(system: ScoreSystem): List<Bucket> = when (system) {
        ScoreSystem.LETTERS -> Letter.entries.map { Bucket(it.name, it.ordinal) }
        ScoreSystem.POINT_100 -> (1..10).map { Bucket("${it * 10}", it * 10) }
        ScoreSystem.POINT_10, ScoreSystem.POINT_10_DECIMAL -> (1..10).map { Bucket("$it", it) }
        ScoreSystem.STARS_5 -> (1..5).map { Bucket("$it", it) }
        ScoreSystem.SMILEY_7 -> Bands.all.reversed().map { Bucket(it.face, it.value, it.value) }
    }

    fun of(raw: Int, system: ScoreSystem): Bucket? {
        if (raw <= 0) return null
        val r = raw.coerceAtMost(100)
        val order = when (system) {
            ScoreSystem.LETTERS -> Letter.of(r)!!.ordinal
            ScoreSystem.POINT_100 -> ((r - 1) / 10 + 1) * 10
            ScoreSystem.POINT_10, ScoreSystem.POINT_10_DECIMAL -> Scores.point10(r)
            ScoreSystem.STARS_5 -> ((Scores.starHalves(r) + 1) / 2).coerceIn(1, 5)
            ScoreSystem.SMILEY_7 -> Bands.of(r)!!.value
        }
        return axis(system).first { it.order == order }
    }

    fun distribution(raws: List<Int>, system: ScoreSystem): List<Pair<Bucket, Int>> {
        val counts = raws.mapNotNull { of(it, system)?.order }.groupingBy { it }.eachCount()
        return axis(system).map { it to (counts[it.order] ?: 0) }
    }

    /** Média e desvio padrão (0–100) das notas dadas; null se nenhuma. */
    fun meanAndDeviation(raws: List<Int>): Pair<Double, Double>? {
        val xs = raws.filter { it > 0 }.map { it.coerceAtMost(100).toDouble() }
        if (xs.isEmpty()) return null
        val mean = xs.average()
        return mean to sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size)
    }
}
