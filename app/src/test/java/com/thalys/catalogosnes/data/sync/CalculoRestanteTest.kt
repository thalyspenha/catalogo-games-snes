package com.thalys.catalogosnes.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculoRestanteTest {

    private val item1 = CatalogoMestreItemDto("A.sfc", "crc-a", 1024, "Jogo A")
    private val item2 = CatalogoMestreItemDto("B.sfc", "crc-b", 2048, "Jogo B")
    private val item3 = CatalogoMestreItemDto("C.sfc", "crc-c", 4096, "Jogo C")
    private val catalogo = listOf(item1, item2, item3)

    @Test
    fun `primeira execucao, sem sucesso gravado, retorna catalogo inteiro`() {
        val restante = calcularRestante(catalogo, crcsComSucesso = emptySet())
        assertEquals(listOf(item1, item2, item3), restante)
    }

    @Test
    fun `retomada parcial, exclui os ja marcados como sucesso`() {
        val restante = calcularRestante(catalogo, crcsComSucesso = setOf("crc-a"))
        assertEquals(listOf(item2, item3), restante)
    }

    @Test
    fun `tudo sincronizado, retorna lista vazia`() {
        val restante = calcularRestante(catalogo, crcsComSucesso = setOf("crc-a", "crc-b", "crc-c"))
        assertEquals(emptyList<CatalogoMestreItemDto>(), restante)
    }

    @Test
    fun `item com falha registrada nao esta em sucesso, entao continua no restante para nova tentativa`() {
        // status FALHA nunca entra no conjunto de crcsComSucesso passado pelo repository,
        // então do ponto de vista deste cálculo é indistinguível de "nunca tentado".
        val restante = calcularRestante(catalogo, crcsComSucesso = setOf("crc-b"))
        assertEquals(listOf(item1, item3), restante)
    }
}
