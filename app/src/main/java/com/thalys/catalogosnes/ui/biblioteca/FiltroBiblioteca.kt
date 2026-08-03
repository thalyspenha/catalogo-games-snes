package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.model.StatusPosse

private const val SEM_GENERO = "Sem gênero"
private const val SEM_ANO = "Sem ano"

/** Filtro ativo na biblioteca: só um por vez (radio-like), aplicado por [filtrarBiblioteca]. */
sealed class FiltroBiblioteca {
    object Todos : FiltroBiblioteca()
    object Tenho : FiltroBiblioteca()
    object QueroTer : FiltroBiblioteca()
    object Faltam : FiltroBiblioteca()
    data class Genero(val valor: String) : FiltroBiblioteca()
    data class Ano(val valor: String) : FiltroBiblioteca()
}

/** Aplica [filtro] sobre [jogos]. `Faltam` inclui `QUERO_TER` e posse nula, propositalmente
 * (jogo ainda falta na coleção mesmo estando na lista de desejos). */
fun filtrarBiblioteca(jogos: List<JogoComPosse>, filtro: FiltroBiblioteca): List<JogoComPosse> =
    when (filtro) {
        FiltroBiblioteca.Todos -> jogos
        FiltroBiblioteca.Tenho -> jogos.filter { it.posse?.status == StatusPosse.TENHO }
        FiltroBiblioteca.QueroTer -> jogos.filter { it.posse?.status == StatusPosse.QUERO_TER }
        FiltroBiblioteca.Faltam -> jogos.filter {
            it.posse?.status != StatusPosse.TENHO && it.posse?.status != StatusPosse.NAO_INTERESSA
        }
        is FiltroBiblioteca.Genero -> jogos.filter { jogoComPosse ->
            val genero = jogoComPosse.jogo.genero?.takeIf { it.isNotBlank() }
            if (filtro.valor == SEM_GENERO) genero == null else genero == filtro.valor
        }
        is FiltroBiblioteca.Ano -> jogos.filter { jogoComPosse ->
            val ano = jogoComPosse.jogo.anoLancamento?.toString()
            if (filtro.valor == SEM_ANO) ano == null else ano == filtro.valor
        }
    }

/** Gêneros distintos presentes em [jogos], A-Z, com "Sem gênero" no fim se houver algum jogo sem gênero (ou em branco). */
fun generosDisponiveis(jogos: List<JogoComPosse>): List<String> {
    val porGenero = jogos.groupBy { it.jogo.genero?.takeIf { genero -> genero.isNotBlank() } }
    val generos = porGenero.keys.filterNotNull().sorted()
    return if (porGenero.containsKey(null)) generos + SEM_GENERO else generos
}

/** Anos distintos presentes em [jogos], cronológico, com "Sem ano" no fim se houver algum jogo sem ano. */
fun anosDisponiveis(jogos: List<JogoComPosse>): List<String> {
    val porAno = jogos.groupBy { it.jogo.anoLancamento }
    val anos = porAno.keys.filterNotNull().sorted().map { it.toString() }
    return if (porAno.containsKey(null)) anos + SEM_ANO else anos
}
