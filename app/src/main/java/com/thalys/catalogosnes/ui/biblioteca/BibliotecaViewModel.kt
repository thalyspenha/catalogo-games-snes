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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Estado exibido pela [com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca]. */
data class BibliotecaUiState(
    val linhas: List<LinhaCarrossel> = emptyList(),
    val resultadoBusca: List<JogoComPosse>? = null,
    val carregando: Boolean = true,
)

/**
 * Observa a biblioteca completa (jogo + posse do usuário) via [JogoRepository], agrupa em
 * carrosséis por categoria via [montarCarrosseis] e expõe como [StateFlow] para a UI.
 * Combina com a consulta de busca ([aoMudarConsultaBusca]): consulta em branco mantém
 * [BibliotecaUiState.resultadoBusca] como `null` (mostra carrosséis); não-vazia filtra a
 * lista via [filtrarPorNome] (mostra grid de resultado). [montarCarrosseis] só recomputa
 * quando o repositório re-emite (ex: sync), não a cada tecla digitada.
 */
class BibliotecaViewModel(
    repository: JogoRepository,
) : ViewModel() {

    private val _consultaBusca = MutableStateFlow("")

    /** Exposta separada de [estadoUi] pra o campo de busca mostrar o texto digitado sem
     * depender da primeira emissão do repositório (ainda não aconteceu enquanto carregando). */
    val consultaBusca: StateFlow<String> = _consultaBusca.asStateFlow()

    val estadoUi: StateFlow<BibliotecaUiState> = combine(
        repository.observarBiblioteca().map { jogos -> jogos to montarCarrosseis(jogos) },
        _consultaBusca,
    ) { (jogos, linhas), consulta ->
        BibliotecaUiState(
            linhas = linhas,
            resultadoBusca = if (consulta.isBlank()) null else filtrarPorNome(jogos, consulta),
            carregando = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BibliotecaUiState(),
    )

    fun aoMudarConsultaBusca(texto: String) {
        _consultaBusca.value = texto
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
