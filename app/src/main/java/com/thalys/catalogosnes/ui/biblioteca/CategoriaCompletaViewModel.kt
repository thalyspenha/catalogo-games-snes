package com.thalys.catalogosnes.ui.biblioteca

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.repository.JogoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Estado exibido pela [com.thalys.catalogosnes.ui.biblioteca.TelaCategoriaCompleta]. */
data class CategoriaCompletaUiState(
    val titulo: String,
    val jogos: List<JogoComPosse> = emptyList(),
    val carregando: Boolean = true,
)

/**
 * Observa a biblioteca completa, reagrupa via [montarCarrosseis] e expõe só os jogos da
 * linha cujo título bate com [titulo] — usado pela tela "Ver tudo" de uma categoria. Reaproveita
 * a função de agrupamento já testada em vez de duplicar lógica de filtro.
 */
class CategoriaCompletaViewModel(
    repository: JogoRepository,
    titulo: String,
) : ViewModel() {

    val estadoUi: StateFlow<CategoriaCompletaUiState> = repository.observarBiblioteca()
        .map { jogos ->
            val jogosDaLinha = montarCarrosseis(jogos).firstOrNull { it.titulo == titulo }?.jogos ?: emptyList()
            CategoriaCompletaUiState(titulo = titulo, jogos = jogosDaLinha, carregando = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriaCompletaUiState(titulo = titulo),
        )

    /** Factory manual, no mesmo espírito de [BibliotecaViewModel.Factory] — aqui com um
     * argumento extra ([titulo]) além do context, mesmo padrão de `DetalheJogoViewModel.Factory`. */
    class Factory(
        private val context: Context,
        private val titulo: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = JogoRepository.obterInstancia(context.applicationContext)
            return CategoriaCompletaViewModel(repository, titulo) as T
        }
    }
}
