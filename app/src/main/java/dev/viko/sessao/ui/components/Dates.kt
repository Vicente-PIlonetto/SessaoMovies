package dev.viko.sessao.ui.components

import dev.viko.sessao.domain.AiredEpisode
import dev.viko.sessao.i18n.t
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Datas no formato curto do Brasil: "dom., 14 dez.", "14 dez. 2025", "dezembro de 2025". */
object Dates {
    private val ptBR = Locale.forLanguageTag("pt-BR")
    private val dayShort = DateTimeFormatter.ofPattern("EEE, d MMM", ptBR)
    private val full = DateTimeFormatter.ofPattern("d MMM yyyy", ptBR)
    private val month = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", ptBR)

    fun parse(s: String?): LocalDate? = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun of(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

    fun short(d: LocalDate): String = d.format(dayShort).withDots()
    fun full(d: LocalDate): String = d.format(full).withDots()
    fun month(d: LocalDate): String = d.format(month).replaceFirstChar { it.uppercase() }
    fun weekday(d: LocalDate): String = d.format(weekday).replaceFirstChar { it.uppercase() }

    /** O Java escreve "dom." e "dez." às vezes sem o ponto; deixa sempre com ponto. */
    private fun String.withDots(): String = split(' ').joinToString(" ") { w ->
        if (w.length in 3..4 && w.all { it.isLetter() } && !w.endsWith(".")) "$w." else w
    }

    /** "hoje", "amanhã", "em 3 d" ou "há 2 d". */
    fun relative(d: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(today, d)
        return when {
            days == 0L -> t("hoje")
            days == 1L -> t("amanhã")
            days == -1L -> t("ontem")
            days > 1 -> t("em {0} d", days)
            else -> t("há {0} d", -days)
        }
    }

    /** "T3 · E1 em 3 d · dom., 14 dez." para o próximo episódio anunciado. */
    fun nextEpisode(e: AiredEpisode, today: LocalDate = LocalDate.now()): String {
        val d = parse(e.airDate) ?: return e.ep.label
        return "${e.ep.label} ${relative(d, today)} · ${short(d)}"
    }
}
