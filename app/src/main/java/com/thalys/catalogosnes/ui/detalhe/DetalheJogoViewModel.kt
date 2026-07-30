package com.thalys.catalogosnes.ui.detalhe

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thalys.catalogosnes.data.local.JogoEntity
import com.thalys.catalogosnes.data.local.PosseUsuarioEntity
import com.thalys.catalogosnes.data.model.StatusPosse
import com.thalys.catalogosnes.data.repository.JogoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado exibido/editado pela `TelaDetalheJogo`. */
data class DetalheUiState(
    val jogo: JogoEntity? = null,
    val status: StatusPosse? = null,
    val temCartucho: Boolean = false,
    val temCaixa: Boolean = false,
    val temManual: Boolean = false,
    val notaCondicao: String = "",
    // Caminho de arquivo da foto da cópia. Só o campo fica pronto nesta etapa: o picker de
    // imagem/câmera é trabalho de UI mais complexo e fica fora de escopo por ora (ver TODO na tela).
    val caminhoFoto: String? = null,
    val carregando: Boolean = true,
    val jogoNaoEncontrado: Boolean = false,
    val salvo: Boolean = false,
)

/**
 * Carrega um jogo específico (com posse, se já existir) e permite editar/salvar o status de
 * posse do usuário via [JogoRepository]. Um ViewModel por jogo aberto (recebe o [jogoId] no
 * construtor via [Factory], não por SavedStateHandle, para manter a implementação simples).
 */
class DetalheJogoViewModel(
    private val repository: JogoRepository,
    private val jogoId: Long,
) : ViewModel() {

    private val _estadoUi = MutableStateFlow(DetalheUiState())
    val estadoUi: StateFlow<DetalheUiState> = _estadoUi.asStateFlow()

    init {
        carregarJogo()
    }

    private fun carregarJogo() {
        viewModelScope.launch {
            val jogoComPosse = repository.buscarJogo(jogoId)
            if (jogoComPosse == null) {
                _estadoUi.update { it.copy(carregando = false, jogoNaoEncontrado = true) }
                return@launch
            }
            val posse = jogoComPosse.posse
            _estadoUi.update {
                it.copy(
                    jogo = jogoComPosse.jogo,
                    status = posse?.status,
                    temCartucho = posse?.temCartucho ?: false,
                    temCaixa = posse?.temCaixa ?: false,
                    temManual = posse?.temManual ?: false,
                    notaCondicao = posse?.notaCondicao ?: "",
                    caminhoFoto = posse?.caminhoFoto,
                    carregando = false,
                )
            }
        }
    }

    fun alterarStatus(novoStatus: StatusPosse) {
        _estadoUi.update { it.copy(status = novoStatus) }
    }

    fun alterarTemCartucho(valor: Boolean) {
        _estadoUi.update { it.copy(temCartucho = valor) }
    }

    fun alterarTemCaixa(valor: Boolean) {
        _estadoUi.update { it.copy(temCaixa = valor) }
    }

    fun alterarTemManual(valor: Boolean) {
        _estadoUi.update { it.copy(temManual = valor) }
    }

    fun alterarNotaCondicao(valor: String) {
        _estadoUi.update { it.copy(notaCondicao = valor) }
    }

    /** Persiste a posse atual. Não faz nada se nenhum status foi escolhido ainda. */
    fun salvar() {
        val estado = _estadoUi.value
        val status = estado.status ?: return
        viewModelScope.launch {
            repository.salvarPosse(
                PosseUsuarioEntity(
                    jogoId = jogoId,
                    status = status,
                    temCartucho = estado.temCartucho,
                    temCaixa = estado.temCaixa,
                    temManual = estado.temManual,
                    caminhoFoto = estado.caminhoFoto,
                    notaCondicao = estado.notaCondicao.ifBlank { null },
                    atualizadoEm = System.currentTimeMillis(),
                )
            )
            _estadoUi.update { it.copy(salvo = true) }
        }
    }

    class Factory(
        private val context: Context,
        private val jogoId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = JogoRepository.obterInstancia(context.applicationContext)
            return DetalheJogoViewModel(repository, jogoId) as T
        }
    }
}
