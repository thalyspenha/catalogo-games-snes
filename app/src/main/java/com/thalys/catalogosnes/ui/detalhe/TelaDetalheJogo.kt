package com.thalys.catalogosnes.ui.detalhe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.thalys.catalogosnes.data.local.modeloCapa
import com.thalys.catalogosnes.data.model.StatusPosse

/**
 * Tela de detalhe + edição de posse de um jogo. Mostra os metadados do catálogo
 * ([com.thalys.catalogosnes.data.local.JogoEntity]) e permite editar status, completude CIB
 * e nota de condição, salvando via [DetalheJogoViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDetalheJogo(
    jogoId: Long,
    aoVoltar: () -> Unit,
    viewModel: DetalheJogoViewModel = viewModel(
        factory = DetalheJogoViewModel.Factory(LocalContext.current, jogoId)
    ),
) {
    val estado by viewModel.estadoUi.collectAsStateWithLifecycle()

    // Depois de salvar com sucesso, volta para a biblioteca.
    LaunchedEffect(estado.salvo) {
        if (estado.salvo) aoVoltar()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(estado.jogo?.nome ?: "") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        }
    ) { paddingInterno ->
        when {
            estado.carregando -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingInterno),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            estado.jogoNaoEncontrado -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingInterno),
                contentAlignment = Alignment.Center,
            ) {
                Text("Jogo não encontrado.")
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingInterno)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                AsyncImage(
                    model = estado.jogo?.modeloCapa(),
                    contentDescription = estado.jogo?.nome,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                )

                Column(modifier = Modifier.padding(top = 16.dp)) {
                    val jogo = estado.jogo
                    val infoSecundaria = listOfNotNull(
                        jogo?.anoLancamento?.toString(),
                        jogo?.genero,
                    ).joinToString(" · ")
                    if (infoSecundaria.isNotBlank()) {
                        Text(text = infoSecundaria, style = MaterialTheme.typography.bodyMedium)
                    }
                    val desenvolvedoraPublicadora = listOfNotNull(jogo?.desenvolvedora, jogo?.publicadora)
                        .distinct()
                        .joinToString(" · ")
                    if (desenvolvedoraPublicadora.isNotBlank()) {
                        Text(
                            text = desenvolvedoraPublicadora,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!jogo?.descricao.isNullOrBlank()) {
                        Text(
                            text = jogo?.descricao.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                Text(
                    text = "Meu status",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                SeletorStatus(statusSelecionado = estado.status, aoSelecionar = viewModel::alterarStatus)

                Text(
                    text = "Completude (CIB)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                LinhaToggle(
                    rotulo = "Cartucho",
                    marcado = estado.temCartucho,
                    aoAlterar = viewModel::alterarTemCartucho,
                )
                LinhaToggle(
                    rotulo = "Caixa",
                    marcado = estado.temCaixa,
                    aoAlterar = viewModel::alterarTemCaixa,
                )
                LinhaToggle(
                    rotulo = "Manual",
                    marcado = estado.temManual,
                    aoAlterar = viewModel::alterarTemManual,
                )

                OutlinedTextField(
                    value = estado.notaCondicao,
                    onValueChange = viewModel::alterarNotaCondicao,
                    label = { Text("Nota de condição") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                )

                // TODO: seletor/captura de foto da própria cópia fica para uma etapa futura —
                // exige picker de imagem/câmera (permissões, armazenamento de arquivo), o que é
                // trabalho de UI mais complexo e está fora do escopo desta etapa. O campo
                // `caminhoFoto` já existe no estado/model, pronto para receber o caminho do
                // arquivo quando essa funcionalidade for implementada.
                Text(
                    text = "Foto da cópia: em breve",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )

                Button(
                    onClick = viewModel::salvar,
                    enabled = estado.status != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp),
                ) {
                    Text("Salvar")
                }
            }
        }
    }
}

@Composable
private fun SeletorStatus(statusSelecionado: StatusPosse?, aoSelecionar: (StatusPosse) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotaoStatus(StatusPosse.TENHO, "Tenho", statusSelecionado, aoSelecionar)
        BotaoStatus(StatusPosse.QUERO_TER, "Quero ter", statusSelecionado, aoSelecionar)
        BotaoStatus(StatusPosse.NAO_INTERESSA, "Não interessa", statusSelecionado, aoSelecionar)
    }
}

@Composable
private fun BotaoStatus(
    status: StatusPosse,
    rotulo: String,
    statusSelecionado: StatusPosse?,
    aoSelecionar: (StatusPosse) -> Unit,
) {
    if (statusSelecionado == status) {
        Button(onClick = { aoSelecionar(status) }) { Text(rotulo) }
    } else {
        OutlinedButton(onClick = { aoSelecionar(status) }) { Text(rotulo) }
    }
}

@Composable
private fun LinhaToggle(rotulo: String, marcado: Boolean, aoAlterar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = marcado, onCheckedChange = aoAlterar)
        Text(rotulo)
    }
}
