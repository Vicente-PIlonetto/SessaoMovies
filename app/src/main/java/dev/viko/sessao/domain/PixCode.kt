package dev.viko.sessao.domain

import java.text.Normalizer

/**
 * Código Pix "copia e cola" estático (BR Code), no padrão EMV do Banco Central.
 * Sem valor: quem doa escolhe quanto no app do banco.
 */
object PixCode {
    fun build(key: String, name: String, city: String, txid: String = "***"): String {
        val account = field("00", "br.gov.bcb.pix") + field("01", key.trim())
        val payload = field("00", "01") +
            field("26", account) +
            field("52", "0000") +
            field("53", "986") +
            field("58", "BR") +
            field("59", clean(name, 25)) +
            field("60", clean(city, 15)) +
            field("62", field("05", txid))
        val withCrcId = "${payload}6304"
        return withCrcId + crc16(withCrcId)
    }

    /** Campo EMV: id + tamanho com 2 dígitos + valor. */
    private fun field(id: String, value: String) = id + value.length.toString().padStart(2, '0') + value

    /** Sem acentos e no tamanho máximo do campo (o padrão só aceita ASCII). */
    fun clean(s: String, max: Int): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .filter { it.code in 32..126 }.take(max)

    /** CRC16-CCITT (polinômio 0x1021, início 0xFFFF), em 4 dígitos hexadecimais maiúsculos. */
    fun crc16(s: String): String {
        var crc = 0xFFFF
        for (b in s.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}
