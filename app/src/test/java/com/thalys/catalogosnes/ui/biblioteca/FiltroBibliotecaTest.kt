package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.local.JogoEntity
import com.thalys.catalogosnes.data.local.PosseUsuarioEntity
import com.thalys.catalogosnes.data.model.StatusPosse
import org.junit.Assert.assertEquals
import org.junit.Test

class FiltroBibliotecaTest {

    private fun jogo(
        id: Long,
        nome: String = "J$id",
        genero: String? = "Ação",
        ano: Int? = 1994,
    ) = JogoEntity(
        id = id,
        nome = nome,
        descricao = null,
        anoLancamento = ano,
        genero = genero,
        desenvolvedora = null,
        publicadora = null,
        urlCapa = null,
        regiao = null,
    )

    private fun posse(jogoId: Long, status: StatusPosse) = PosseUsuarioEntity(
        jogoId = jogoId,
        status = status,
        atualizadoEm = 0L,
    )

    @Test
    fun `filtro Todos retorna a lista inteira`() {
        val jogos = listOf(JogoComPosse(jogo(1), null), JogoComPosse(jogo(2), null))
        assertEquals(jogos, filtrarBiblioteca(jogos, FiltroBiblioteca.Todos))
    }

    @Test
    fun `filtro Tenho retorna so status TENHO`() {
        val jogos = listOf(
            JogoComPosse(jogo(1), posse(1, StatusPosse.TENHO)),
            JogoComPosse(jogo(2), posse(2, StatusPosse.QUERO_TER)),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Tenho)
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro QueroTer retorna so status QUERO_TER`() {
        val jogos = listOf(
            JogoComPosse(jogo(1), posse(1, StatusPosse.QUERO_TER)),
            JogoComPosse(jogo(2), posse(2, StatusPosse.TENHO)),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.QueroTer)
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Faltam inclui quero ter e sem posse, exclui tenho e nao interessa`() {
        val jogos = listOf(
            JogoComPosse(jogo(1), posse(1, StatusPosse.QUERO_TER)),
            JogoComPosse(jogo(2), posse(2, StatusPosse.TENHO)),
            JogoComPosse(jogo(3), posse(3, StatusPosse.NAO_INTERESSA)),
            JogoComPosse(jogo(4), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Faltam)
        assertEquals(listOf(1L, 4L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Genero com valor real casa pelo nome exato`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, genero = "RPG"), null),
            JogoComPosse(jogo(2, genero = "Ação"), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Genero("RPG"))
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Genero Sem genero casa jogos sem genero ou com genero em branco`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, genero = "RPG"), null),
            JogoComPosse(jogo(2, genero = null), null),
            JogoComPosse(jogo(3, genero = "   "), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Genero("Sem gênero"))
        assertEquals(listOf(2L, 3L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Ano com valor real casa pelo ano exato`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, ano = 1994), null),
            JogoComPosse(jogo(2, ano = 1996), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Ano("1994"))
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Ano Sem ano casa jogos sem ano cadastrado`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, ano = 1994), null),
            JogoComPosse(jogo(2, ano = null), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Ano("Sem ano"))
        assertEquals(listOf(2L), resultado.map { it.jogo.id })
    }

    @Test
    fun `generosDisponiveis ordena alfabetico e poe Sem genero no fim`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, genero = "RPG"), null),
            JogoComPosse(jogo(2, genero = "Ação"), null),
            JogoComPosse(jogo(3, genero = null), null),
        )
        assertEquals(listOf("Ação", "RPG", "Sem gênero"), generosDisponiveis(jogos))
    }

    @Test
    fun `generosDisponiveis sem nenhum jogo sem genero, nao inclui Sem genero`() {
        val jogos = listOf(JogoComPosse(jogo(1, genero = "RPG"), null))
        assertEquals(listOf("RPG"), generosDisponiveis(jogos))
    }

    @Test
    fun `anosDisponiveis ordena cronologico e poe Sem ano no fim`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, ano = 1996), null),
            JogoComPosse(jogo(2, ano = 1992), null),
            JogoComPosse(jogo(3, ano = null), null),
        )
        assertEquals(listOf("1992", "1996", "Sem ano"), anosDisponiveis(jogos))
    }

    @Test
    fun `anosDisponiveis sem nenhum jogo sem ano, nao inclui Sem ano`() {
        val jogos = listOf(JogoComPosse(jogo(1, ano = 1994), null))
        assertEquals(listOf("1994"), anosDisponiveis(jogos))
    }

    @Test
    fun `filtrarPorNome acha substring no meio do nome, case-insensitive`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "The Legend of Zelda: A Link to the Past"), null),
            JogoComPosse(jogo(2, "Chrono Trigger"), null),
        )
        val resultado = filtrarPorNome(jogos, "zelda")
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtrarPorNome sem nenhum resultado retorna lista vazia`() {
        val jogos = listOf(JogoComPosse(jogo(1, "Chrono Trigger"), null))
        val resultado = filtrarPorNome(jogos, "mario")
        assertEquals(emptyList<JogoComPosse>(), resultado)
    }

    @Test
    fun `filtrarPorNome com consulta em branco retorna a lista inteira`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), null),
            JogoComPosse(jogo(2, "B"), null),
        )
        assertEquals(jogos, filtrarPorNome(jogos, ""))
        assertEquals(jogos, filtrarPorNome(jogos, "   "))
    }

    @Test
    fun `filtrarPorNome ignora espaco no inicio e fim da consulta`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "The Legend of Zelda: A Link to the Past"), null),
            JogoComPosse(jogo(2, "Chrono Trigger"), null),
        )
        val resultado = filtrarPorNome(jogos, "zelda ")
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }
}
