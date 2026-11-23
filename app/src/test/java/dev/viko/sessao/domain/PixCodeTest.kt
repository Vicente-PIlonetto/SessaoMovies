package dev.viko.sessao.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixCodeTest {
    @Test fun officialExampleCrc() {
        // Exemplo do manual do BR Code do Banco Central.
        val body = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***6304"
        assertEquals("1D3D", PixCode.crc16(body))
    }

    @Test fun buildsTheSamePayloadAsTheManual() {
        val code = PixCode.build("123e4567-e12b-12d1-a456-426655440000", "Fulano de Tal", "BRASILIA")
        assertEquals("00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D", code)
    }

    @Test fun removesAccentsAndLimitsLength() {
        assertEquals("Sao Joao da Boa", PixCode.clean("São João da Boa Vista", 15))
        val code = PixCode.build("chave", "Vicente Pilonetto", "Florianópolis")
        assertTrue(code.contains("6013Florianopolis"))
    }
}
