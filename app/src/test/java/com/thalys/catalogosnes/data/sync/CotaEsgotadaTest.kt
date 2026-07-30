package com.thalys.catalogosnes.data.sync

import com.thalys.catalogosnes.data.remote.screenscraper.dto.HeaderDto
import com.thalys.catalogosnes.data.remote.screenscraper.dto.JogoInfoRespostaDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CotaEsgotadaTest {

    @Test
    fun `detecta erro mencionando quota no header`() {
        val resposta = JogoInfoRespostaDto(header = HeaderDto(error = "Quota Atteinte"), response = null)
        assertTrue(cotaEsgotada(resposta))
    }

    @Test
    fun `nao detecta cota quando nao ha erro`() {
        val resposta = JogoInfoRespostaDto(header = HeaderDto(error = null), response = null)
        assertFalse(cotaEsgotada(resposta))
    }

    @Test
    fun `nao detecta cota em erro nao relacionado`() {
        val resposta = JogoInfoRespostaDto(header = HeaderDto(error = "Erreur de login"), response = null)
        assertFalse(cotaEsgotada(resposta))
    }
}
