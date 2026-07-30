package com.thalys.catalogosnes.ui.sincronizacao

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thalys.catalogosnes.data.sync.SincronizacaoEstado
import com.thalys.catalogosnes.data.sync.SincronizacaoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Expõe o progresso do `SincronizacaoRepository` pra `TelaSincronizacao` e controla
 * início/cancelamento. Cancelar só interrompe o coroutine — o progresso já gravado no
 * Room continua valendo como checkpoint pra retomar depois.
 */
class SincronizacaoViewModel(
    private val repository: SincronizacaoRepository,
) : ViewModel() {

    val estado: StateFlow<SincronizacaoEstado> = repository.estado

    private var jobSincronizacao: Job? = null

    fun iniciar() {
        if (jobSincronizacao?.isActive == true) return
        jobSincronizacao = viewModelScope.launch {
            repository.sincronizar()
        }
    }

    fun cancelar() {
        jobSincronizacao?.cancel()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = SincronizacaoRepository.obterInstancia(context.applicationContext)
            return SincronizacaoViewModel(repository) as T
        }
    }
}
