package com.thalys.catalogosnes.ui.biblioteca

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.repository.JogoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Estado exibido pela [com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca]. */
data class BibliotecaUiState(
    val linhas: List<LinhaCarrossel> = emptyList(),
    val resultadoBusca: List<JogoComPosse>? = null,
    val consultaBusca: String = "",
    val carregando: Boolean = true,
)

/**
 * Observa a biblioteca completa (jogo + posse do usuário) via [JogoRepository], agrupa em
 * carrosséis por categoria via [montarCarrosseis] e expõe como [StateFlow] para a UI.
 * Combina com a consulta de busca ([aoMudarConsultaBusca]): consulta em branco mantém
 * [BibliotecaUiState.resultadoBusca] como `null` (mostra carrosséis); não-vazia filtra a
 * lista via [filtrarPorNome] (mostra grid de resultado).
 */
class BibliotecaViewModel(
    repository: JogoRepository,
) : ViewModel() {

    private val consultaBusca = MutableStateFlow("")

    val estadoUi: StateFlow<BibliotecaUiState> = combine(
        repository.observarBiblioteca(),
        consultaBusca,
    ) { jogos, consulta ->
        BibliotecaUiState(
            linhas = montarCarrosseis(jogos),
            resultadoBusca = if (consulta.isBlank()) null else filtrarPorNome(jogos, consulta),
            consultaBusca = consulta,
            carregando = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BibliotecaUiState(),
    )

    fun aoMudarConsultaBusca(texto: String) {
        consultaBusca.value = texto
    }

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
