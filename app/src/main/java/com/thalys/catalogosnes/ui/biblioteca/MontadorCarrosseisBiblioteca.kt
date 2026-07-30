package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.model.StatusPosse

enum class TipoCategoria { MEUS_JOGOS, FALTAM, GENERO, ANO }

/** Uma linha de carrossel na biblioteca: título da categoria + jogos que pertencem a ela. */
data class LinhaCarrossel(
    val titulo: String,
    val jogos: List<JogoComPosse>,
    val tipo: TipoCategoria,
)

private const val TITULO_MEUS_JOGOS = "Meus jogos"
private const val TITULO_FALTAM = "Faltam"
private const val TITULO_SEM_GENERO = "Sem gênero"
private const val TITULO_SEM_ANO = "Sem ano"
private const val CAP_PADRAO_POR_LINHA = 20

/**
 * Agrupa a biblioteca completa em linhas de carrossel, na ordem:
 * Meus jogos -> Faltam -> Gêneros (A-Z, "Sem gênero" no fim) -> Anos (cronológico, "Sem ano" no fim).
 * Categoria sem nenhum jogo não gera linha.
 */
fun montarCarrosseis(jogos: List<JogoComPosse>): List<LinhaCarrossel> {
    val linhas = mutableListOf<LinhaCarrossel>()

    val meusJogos = jogos.filter { it.posse?.status == StatusPosse.TENHO }
    if (meusJogos.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_MEUS_JOGOS, meusJogos, TipoCategoria.MEUS_JOGOS)
    }

    val faltam = jogos.filter {
        it.posse?.status != StatusPosse.TENHO && it.posse?.status != StatusPosse.NAO_INTERESSA
    }
    if (faltam.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_FALTAM, faltam, TipoCategoria.FALTAM)
    }

    val porGenero = jogos.groupBy { it.jogo.genero?.takeIf { genero -> genero.isNotBlank() } }
    porGenero.keys.filterNotNull().sorted().forEach { genero ->
        linhas += LinhaCarrossel(genero, porGenero.getValue(genero), TipoCategoria.GENERO)
    }
    porGenero[null]?.let { semGenero ->
        linhas += LinhaCarrossel(TITULO_SEM_GENERO, semGenero, TipoCategoria.GENERO)
    }

    val porAno = jogos.groupBy { it.jogo.anoLancamento }
    porAno.keys.filterNotNull().sorted().forEach { ano ->
        linhas += LinhaCarrossel(ano.toString(), porAno.getValue(ano), TipoCategoria.ANO)
    }
    porAno[null]?.let { semAno ->
        linhas += LinhaCarrossel(TITULO_SEM_ANO, semAno, TipoCategoria.ANO)
    }

    return linhas
}

/** true quando a linha tem mais jogos que [cap] — sinal pra UI mostrar o card "Ver tudo". */
fun mostrarVerTudo(linha: LinhaCarrossel, cap: Int = CAP_PADRAO_POR_LINHA): Boolean =
    linha.jogos.size > cap

/** Jogos a exibir na linha: a lista inteira se estiver dentro do [cap], senão só os primeiros [cap]. */
fun jogosVisiveis(linha: LinhaCarrossel, cap: Int = CAP_PADRAO_POR_LINHA): List<JogoComPosse> =
    if (linha.jogos.size <= cap) linha.jogos else linha.jogos.take(cap)
