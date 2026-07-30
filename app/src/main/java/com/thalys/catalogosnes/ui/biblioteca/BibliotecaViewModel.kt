package com.thalys.catalogosnes.ui.biblioteca

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thalys.catalogosnes.data.repository.JogoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Estado exibido pela [com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca]. */
data class BibliotecaUiState(
    val linhas: List<LinhaCarrossel> = emptyList(),
    val carregando: Boolean = true,
)

/**
 * Observa a biblioteca completa (jogo + posse do usuário) via [JogoRepository], agrupa em
 * carrosséis por categoria via [montarCarrosseis] e expõe como [StateFlow] para a UI.
 */
class BibliotecaViewModel(
    repository: JogoRepository,
) : ViewModel() {

    val estadoUi: StateFlow<BibliotecaUiState> = repository.observarBiblioteca()
        .map { jogos -> BibliotecaUiState(linhas = montarCarrosseis(jogos), carregando = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BibliotecaUiState(),
        )

    /**
     * Factory manual (sem Hilt/Dagger), no mesmo espírito do `obterInstancia(context)` já
     * usado em [JogoRepository]/`AppDatabase`/`NetworkModule`.
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = JogoRepository.obterInstancia(context.applicationContext)
            return BibliotecaViewModel(repository) as T
        }
    }
}
