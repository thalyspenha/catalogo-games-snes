package com.thalys.catalogosnes.data.sync

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.local.JogoEntity
import com.thalys.catalogosnes.data.local.PosseUsuarioEntity
import com.thalys.catalogosnes.data.model.StatusPosse
import org.junit.Assert.assertEquals
import org.junit.Test

class PossesPendentesTest {

    private fun jogo(id: Long, nome: String) = JogoEntity(
        id = id, nome = nome, descricao = null, anoLancamento = null, genero = null,
        desenvolvedora = null, publicadora = null, urlCapa = null, regiao = null,
    )

    @Test
    fun `normaliza caixa, acento e pontuacao`() {
        assertEquals(
            normalizarNomeParaCasamento("Pokémon: Stadium!"),
            normalizarNomeParaCasamento("pokemon stadium"),
        )
    }

    @Test
    fun `ignora artigo the em qualquer posicao`() {
        assertEquals(
            normalizarNomeParaCasamento("The Legend of Zelda: A Link to the Past"),
            normalizarNomeParaCasamento("Legend of Zelda, The - A Link to Past"),
        )
    }

    @Test
    fun `extrai so jogos com posse e preserva os dados da posse`() {
        val posse = PosseUsuarioEntity(
            jogoId = 1, status = StatusPosse.TENHO, temCartucho = true, temCaixa = false,
            temManual = true, caminhoFoto = null, notaCondicao = "etiqueta gasta", atualizadoEm = 42,
        )
        val pendentes = extrairPossesPendentes(
            listOf(
                JogoComPosse(jogo(1, "Super Mario World"), posse),
                JogoComPosse(jogo(2, "F-Zero"), null),
            )
        )

        assertEquals(1, pendentes.size)
        assertEquals("super mario world", pendentes[0].nomeNormalizado)
        assertEquals(posse.copy(jogoId = 999), pendentes[0].paraEntity(999))
    }
}
