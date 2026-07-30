package com.thalys.catalogosnes.ui.sincronizacao

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thalys.catalogosnes.data.sync.SincronizacaoEstado

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaSincronizacao(
    aoVoltar: () -> Unit,
    viewModel: SincronizacaoViewModel = viewModel(
        factory = SincronizacaoViewModel.Factory(LocalContext.current)
    ),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sincronizar catálogo") }) }
    ) { paddingInterno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingInterno)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val estadoAtual = estado) {
                is SincronizacaoEstado.Ocioso -> {
                    Text("Baixa o catálogo completo de jogos de SNES do ScreenScraper e substitui os dados locais.")
                    Button(onClick = viewModel::iniciar) { Text("Iniciar sincronização") }
                }

                is SincronizacaoEstado.EmAndamento -> {
                    Text("${estadoAtual.atual} de ${estadoAtual.total} jogos")
                    LinearProgressIndicator(
                        progress = {
                            if (estadoAtual.total > 0) {
                                estadoAtual.atual.toFloat() / estadoAtual.total.toFloat()
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(estadoAtual.nomeJogoAtual, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = viewModel::cancelar) { Text("Cancelar") }
                }

                is SincronizacaoEstado.Concluido -> {
                    Text("${estadoAtual.sucesso} sincronizados, ${estadoAtual.falhas.size} falharam")
                    if (estadoAtual.falhas.isNotEmpty()) {
                        Button(onClick = viewModel::iniciar) { Text("Tentar novamente falhas") }
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(estadoAtual.falhas) { falha ->
                                Text(
                                    "${falha.nomeExibicao}: ${falha.motivo}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                is SincronizacaoEstado.CotaEsgotada -> {
                    Text("Cota diária da API esgotada ou muitas falhas de rede seguidas. Tente novamente mais tarde.")
                    Text("${estadoAtual.sucesso} sincronizados até agora, ${estadoAtual.restantes} restantes.")
                }

                is SincronizacaoEstado.Erro -> {
                    Text(estadoAtual.mensagem)
                }
            }
        }
    }
}
