package com.thalys.catalogosnes.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogoMestreLoaderTest {

    @Test
    fun `parseia lista de itens do catalogo mestre a partir do JSON`() {
        val jsonDeTeste = """
            [
              {
                "romNome": "Super Mario World (USA).sfc",
                "crc": "b19ed489",
                "romTamanho": 524288,
                "nomeExibicao": "Super Mario World (USA)"
              }
            ]
        """.trimIndent()

        val resultado = CatalogoMestreLoader.parsear(jsonDeTeste)

        assertEquals(1, resultado.size)
        assertEquals("b19ed489", resultado.first().crc)
        assertEquals("Super Mario World (USA)", resultado.first().nomeExibicao)
    }
}
