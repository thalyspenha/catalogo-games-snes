package com.thalys.catalogosnes.ui.biblioteca

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.thalys.catalogosnes.data.local.JogoComPosse

/**
 * Grid completo (3 colunas) de uma única categoria da biblioteca, aberto a partir do
 * card "Ver tudo" de um carrossel — mesmo layout que existia antes dos carrosséis por
 * categoria, agora reaproveitado só pra uma categoria de cada vez.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaCategoriaCompleta(
    titulo: String,
    aoClicarJogo: (Long) -> Unit,
    aoVoltar: () -> Unit,
    viewModel: CategoriaCompletaViewModel = viewModel(
        factory = CategoriaCompletaViewModel.Factory(LocalContext.current, titulo)
    ),
) {
    val estado by viewModel.estadoUi.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(estado.titulo) },
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingInterno),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> GridDeJogos(
                jogos = estado.jogos,
                aoClicarJogo = aoClicarJogo,
                mensagemVazia = "Nenhum jogo nesta categoria",
                modifier = Modifier.padding(8.dp),
                contentPadding = paddingInterno,
            )
        }
    }
}

/**
 * Grid de 3 colunas com estado vazio embutido — reaproveitado por [TelaCategoriaCompleta]
 * e pelo resultado de busca em [TelaBiblioteca].
 */
@Composable
fun GridDeJogos(
    jogos: List<JogoComPosse>,
    aoClicarJogo: (Long) -> Unit,
    mensagemVazia: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
) {
    if (jogos.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(mensagemVazia)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = contentPadding,
            modifier = modifier,
        ) {
            items(jogos, key = { it.jogo.id }) { jogoComPosse ->
                CartaoJogoGrid(
                    jogoComPosse = jogoComPosse,
                    aoClicar = { aoClicarJogo(jogoComPosse.jogo.id) },
                )
            }
        }
    }
}

@Composable
private fun CartaoJogoGrid(jogoComPosse: JogoComPosse, aoClicar: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .clickable(onClick = aoClicar),
    ) {
        Box {
            AsyncImage(
                model = jogoComPosse.jogo.urlCapa,
                contentDescription = jogoComPosse.jogo.nome,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
            )
            val status = jogoComPosse.posse?.status
            if (status != null) {
                SeloStatus(status = status, modifier = Modifier.padding(6.dp))
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = jogoComPosse.jogo.nome,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
