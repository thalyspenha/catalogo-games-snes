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

private fun tituloFiltro(filtro: FiltroBiblioteca): String = when (filtro) {
    FiltroBiblioteca.Todos -> "Catálogo SNES"
    FiltroBiblioteca.Tenho -> "Tenho"
    FiltroBiblioteca.QueroTer -> "Quero ter"
    FiltroBiblioteca.Faltam -> "Faltam"
    is FiltroBiblioteca.Genero -> filtro.valor
    is FiltroBiblioteca.Ano -> filtro.valor
}

/** Estado exibido pela [com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca]. */
data class BibliotecaUiState(
    val jogosFiltrados: List<JogoComPosse> = emptyList(),
    val filtroSelecionado: FiltroBiblioteca = FiltroBiblioteca.Todos,
    val generosDisponiveis: List<String> = emptyList(),
    val anosDisponiveis: List<String> = emptyList(),
    val resultadoBusca: List<JogoComPosse>? = null,
    val carregando: Boolean = true,
) {
    val tituloTopBar: String get() = tituloFiltro(filtroSelecionado)
}

/**
 * Observa a biblioteca completa via [JogoRepository], aplica o [FiltroBiblioteca] ativo
 * (default [FiltroBiblioteca.Todos] — abre sempre com o catálogo inteiro) via
 * [filtrarBiblioteca], e expõe como [StateFlow] pra UI. Busca por nome (via
 * [aoMudarConsultaBusca]) é independente do filtro: consulta em branco mantém
 * [BibliotecaUiState.resultadoBusca] como `null` (mostra o grid filtrado); não-vazia
 * pesquisa sempre na lista inteira via [filtrarPorNome], ignorando o filtro ativo.
 * [generosDisponiveis]/[anosDisponiveis] são pré-mapeados a partir de
 * [JogoRepository.observarBiblioteca] antes do `combine`, então só recomputam quando o
 * repositório re-emite (ex: sync) — não a cada troca de filtro nem tecla digitada na busca
 * (mesmo cuidado de performance já aplicado no antigo `montarCarrosseis`, fix wave 2026-07-31).
 */
class BibliotecaViewModel(
    repository: JogoRepository,
) : ViewModel() {

    private val _consultaBusca = MutableStateFlow("")
    val consultaBusca: StateFlow<String> = _consultaBusca.asStateFlow()

    private val _filtroSelecionado = MutableStateFlow<FiltroBiblioteca>(FiltroBiblioteca.Todos)

    val estadoUi: StateFlow<BibliotecaUiState> = combine(
        repository.observarBiblioteca().map { jogos ->
            Triple(jogos, generosDisponiveis(jogos), anosDisponiveis(jogos))
        },
        _filtroSelecionado,
        _consultaBusca,
    ) { (jogos, generos, anos), filtro, consulta ->
        BibliotecaUiState(
            jogosFiltrados = filtrarBiblioteca(jogos, filtro),
            filtroSelecionado = filtro,
            generosDisponiveis = generos,
            anosDisponiveis = anos,
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

    fun aoSelecionarFiltro(filtro: FiltroBiblioteca) {
        _filtroSelecionado.value = filtro
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
