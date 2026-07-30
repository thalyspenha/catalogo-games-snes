package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.local.JogoEntity
import com.thalys.catalogosnes.data.local.PosseUsuarioEntity
import com.thalys.catalogosnes.data.model.StatusPosse
import org.junit.Assert.assertEquals
import org.junit.Test

class MontadorCarrosseisBibliotecaTest {

    private fun jogo(
        id: Long,
        nome: String,
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
    fun `ordem das categorias e meus jogos, faltam, generos, anos`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG", ano = 1994), posse(1, StatusPosse.TENHO)),
            JogoComPosse(jogo(2, "B", genero = "Ação", ano = 1995), null),
        )

        val linhas = montarCarrosseis(jogos)

        assertEquals(
            listOf("Meus jogos", "Faltam", "Ação", "RPG", "1994", "1995"),
            linhas.map { it.titulo },
        )
    }

    @Test
    fun `generos ficam em ordem alfabetica`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG"), null),
            JogoComPosse(jogo(2, "B", genero = "Ação"), null),
            JogoComPosse(jogo(3, "C", genero = "Luta"), null),
        )

        val linhas = montarCarrosseis(jogos)
        val titulosGenero = linhas.map { it.titulo }.filter { it in listOf("RPG", "Ação", "Luta") }

        assertEquals(listOf("Ação", "Luta", "RPG"), titulosGenero)
    }

    @Test
    fun `anos ficam em ordem cronologica`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", ano = 1996), null),
            JogoComPosse(jogo(2, "B", ano = 1992), null),
            JogoComPosse(jogo(3, "C", ano = 1994), null),
        )

        val linhas = montarCarrosseis(jogos)
        val titulosAno = linhas.map { it.titulo }.filter { it in listOf("1996", "1992", "1994") }

        assertEquals(listOf("1992", "1994", "1996"), titulosAno)
    }

    @Test
    fun `jogo sem genero cai em sem genero, no fim do bloco de generos`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG"), null),
            JogoComPosse(jogo(2, "B", genero = null), null),
        )

        val linhas = montarCarrosseis(jogos)
        val linhaSemGenero = linhas.first { it.titulo == "Sem gênero" }

        assertEquals(listOf(2L), linhaSemGenero.jogos.map { it.jogo.id })
        assertEquals(listOf("RPG", "Sem gênero"), linhas.map { it.titulo }.filter { it == "RPG" || it == "Sem gênero" })
    }

    @Test
    fun `jogo com genero em branco cai em sem genero, nao vira linha com titulo vazio`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG"), null),
            JogoComPosse(jogo(2, "B", genero = ""), null),
            JogoComPosse(jogo(3, "C", genero = "   "), null),
        )

        val linhas = montarCarrosseis(jogos)

        assertEquals(false, linhas.any { it.titulo.isBlank() })
        val linhaSemGenero = linhas.first { it.titulo == "Sem gênero" }
        assertEquals(listOf(2L, 3L), linhaSemGenero.jogos.map { it.jogo.id })
    }

    @Test
    fun `jogo sem ano cai em sem ano, no fim do bloco de anos`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", ano = 1994), null),
            JogoComPosse(jogo(2, "B", ano = null), null),
        )

        val linhas = montarCarrosseis(jogos)
        val linhaSemAno = linhas.first { it.titulo == "Sem ano" }

        assertEquals(listOf(2L), linhaSemAno.jogos.map { it.jogo.id })
        assertEquals(listOf("1994", "Sem ano"), linhas.map { it.titulo }.filter { it == "1994" || it == "Sem ano" })
    }

    @Test
    fun `nao interessa nao aparece em faltam`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.NAO_INTERESSA)),
        )

        val linhas = montarCarrosseis(jogos)

        assertEquals(null, linhas.firstOrNull { it.titulo == "Faltam" })
    }

    @Test
    fun `jogo com status tenho nao aparece em faltam`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.TENHO)),
        )

        val linhas = montarCarrosseis(jogos)
        val faltam = linhas.firstOrNull { it.titulo == "Faltam" }

        assertEquals(null, faltam)
    }

    @Test
    fun `sem posse registrada conta como faltam`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), null),
        )

        val linhas = montarCarrosseis(jogos)
        val faltam = linhas.first { it.titulo == "Faltam" }

        assertEquals(listOf(1L), faltam.jogos.map { it.jogo.id })
    }

    @Test
    fun `categoria sem jogos nao gera linha`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.NAO_INTERESSA)),
        )

        val linhas = montarCarrosseis(jogos)

        assertEquals(false, linhas.any { it.jogos.isEmpty() })
    }

    @Test
    fun `lista vazia produz nenhuma linha`() {
        val linhas = montarCarrosseis(emptyList())

        assertEquals(emptyList<LinhaCarrossel>(), linhas)
    }

    @Test
    fun `linha de meus jogos tem tipo MEUS_JOGOS`() {
        val jogos = listOf(JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.TENHO)))
        val linhas = montarCarrosseis(jogos)
        assertEquals(TipoCategoria.MEUS_JOGOS, linhas.first { it.titulo == "Meus jogos" }.tipo)
    }

    @Test
    fun `linha de faltam tem tipo FALTAM`() {
        val jogos = listOf(JogoComPosse(jogo(1, "A"), null))
        val linhas = montarCarrosseis(jogos)
        assertEquals(TipoCategoria.FALTAM, linhas.first { it.titulo == "Faltam" }.tipo)
    }

    @Test
    fun `linhas de genero e sem genero tem tipo GENERO`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG"), null),
            JogoComPosse(jogo(2, "B", genero = null), null),
        )
        val linhas = montarCarrosseis(jogos)
        assertEquals(TipoCategoria.GENERO, linhas.first { it.titulo == "RPG" }.tipo)
        assertEquals(TipoCategoria.GENERO, linhas.first { it.titulo == "Sem gênero" }.tipo)
    }

    @Test
    fun `linhas de ano e sem ano tem tipo ANO`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", ano = 1994), null),
            JogoComPosse(jogo(2, "B", ano = null), null),
        )
        val linhas = montarCarrosseis(jogos)
        assertEquals(TipoCategoria.ANO, linhas.first { it.titulo == "1994" }.tipo)
        assertEquals(TipoCategoria.ANO, linhas.first { it.titulo == "Sem ano" }.tipo)
    }

    @Test
    fun `mostrarVerTudo false com 20 jogos ou menos`() {
        val linha = LinhaCarrossel(
            titulo = "X",
            jogos = List(20) { i -> JogoComPosse(jogo(i.toLong(), "J$i"), null) },
            tipo = TipoCategoria.GENERO,
        )
        assertEquals(false, mostrarVerTudo(linha))
    }

    @Test
    fun `mostrarVerTudo true com mais de 20 jogos`() {
        val linha = LinhaCarrossel(
            titulo = "X",
            jogos = List(21) { i -> JogoComPosse(jogo(i.toLong(), "J$i"), null) },
            tipo = TipoCategoria.GENERO,
        )
        assertEquals(true, mostrarVerTudo(linha))
    }

    @Test
    fun `jogosVisiveis retorna lista inteira quando dentro do cap`() {
        val jogos = List(15) { i -> JogoComPosse(jogo(i.toLong(), "J$i"), null) }
        val linha = LinhaCarrossel(titulo = "X", jogos = jogos, tipo = TipoCategoria.GENERO)
        assertEquals(15, jogosVisiveis(linha).size)
    }

    @Test
    fun `jogosVisiveis corta nos primeiros 20 quando passa do cap`() {
        val jogos = List(25) { i -> JogoComPosse(jogo(i.toLong(), "J$i"), null) }
        val linha = LinhaCarrossel(titulo = "X", jogos = jogos, tipo = TipoCategoria.GENERO)
        val visiveis = jogosVisiveis(linha)
        assertEquals(20, visiveis.size)
        assertEquals((0..19).map { it.toLong() }, visiveis.map { it.jogo.id })
    }
}
