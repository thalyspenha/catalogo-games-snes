package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.model.StatusPosse

/** Uma linha de carrossel na biblioteca: título da categoria + jogos que pertencem a ela. */
data class LinhaCarrossel(
    val titulo: String,
    val jogos: List<JogoComPosse>,
)

private const val TITULO_MEUS_JOGOS = "Meus jogos"
private const val TITULO_FALTAM = "Faltam"
private const val TITULO_SEM_GENERO = "Sem gênero"
private const val TITULO_SEM_ANO = "Sem ano"

/**
 * Agrupa a biblioteca completa em linhas de carrossel, na ordem:
 * Meus jogos -> Faltam -> Gêneros (A-Z, "Sem gênero" no fim) -> Anos (cronológico, "Sem ano" no fim).
 * Categoria sem nenhum jogo não gera linha.
 */
fun montarCarrosseis(jogos: List<JogoComPosse>): List<LinhaCarrossel> {
    val linhas = mutableListOf<LinhaCarrossel>()

    val meusJogos = jogos.filter { it.posse?.status == StatusPosse.TENHO }
    if (meusJogos.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_MEUS_JOGOS, meusJogos)
    }

    val faltam = jogos.filter {
        it.posse?.status != StatusPosse.TENHO && it.posse?.status != StatusPosse.NAO_INTERESSA
    }
    if (faltam.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_FALTAM, faltam)
    }

    val porGenero = jogos.groupBy { it.jogo.genero }
    porGenero.keys.filterNotNull().sorted().forEach { genero ->
        linhas += LinhaCarrossel(genero, porGenero.getValue(genero))
    }
    porGenero[null]?.let { semGenero ->
        linhas += LinhaCarrossel(TITULO_SEM_GENERO, semGenero)
    }

    val porAno = jogos.groupBy { it.jogo.anoLancamento }
    porAno.keys.filterNotNull().sorted().forEach { ano ->
        linhas += LinhaCarrossel(ano.toString(), porAno.getValue(ano))
    }
    porAno[null]?.let { semAno ->
        linhas += LinhaCarrossel(TITULO_SEM_ANO, semAno)
    }

    return linhas
}
