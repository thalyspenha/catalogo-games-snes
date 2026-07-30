package com.thalys.catalogosnes.ui.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.model.StatusPosse
import com.thalys.catalogosnes.ui.theme.CatalogoSnesTheme
import com.thalys.catalogosnes.ui.theme.SnesRoxoClaro
import com.thalys.catalogosnes.ui.theme.SnesVerde
import com.thalys.catalogosnes.ui.theme.SnesVermelho
import kotlinx.coroutines.launch

/**
 * Biblioteca principal (estilo Netflix): carrosséis horizontais por categoria
 * (Meus jogos, Faltam, Gênero, Ano), montados por [montarCarrosseis]. Barra de chips no
 * topo permite pular direto pra qualquer linha; linhas com mais de 20 jogos ganham um
 * card "Ver tudo" que abre a tela de grid completo daquela categoria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaBiblioteca(
    aoClicarJogo: (Long) -> Unit,
    aoClicarSincronizar: () -> Unit,
    aoClicarVerTudo: (String) -> Unit,
    viewModel: BibliotecaViewModel = viewModel(
        factory = BibliotecaViewModel.Factory(LocalContext.current)
    ),
) {
    val estado by viewModel.estadoUi.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val escopo = rememberCoroutineScope()
    var chipExpandido by remember { mutableStateOf<TipoCategoria?>(null) }

    fun rolarParaTitulo(titulo: String) {
        val indice = estado.linhas.indexOfFirst { it.titulo == titulo }
        if (indice >= 0) {
            escopo.launch { listState.animateScrollToItem(indice) }
        }
    }

    fun rolarParaPrimeiraDoTipo(tipo: TipoCategoria) {
        val indice = estado.linhas.indexOfFirst { it.tipo == tipo }
        if (indice >= 0) {
            escopo.launch { listState.animateScrollToItem(indice) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catálogo SNES") },
                actions = {
                    IconButton(onClick = aoClicarSincronizar) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar catálogo")
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

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingInterno),
            ) {
                BarraDeIndice(
                    linhas = estado.linhas,
                    chipExpandido = chipExpandido,
                    aoTocarMeusJogos = { rolarParaPrimeiraDoTipo(TipoCategoria.MEUS_JOGOS) },
                    aoTocarFaltam = { rolarParaPrimeiraDoTipo(TipoCategoria.FALTAM) },
                    aoAlternarGenero = {
                        chipExpandido = if (chipExpandido == TipoCategoria.GENERO) null else TipoCategoria.GENERO
                    },
                    aoAlternarAno = {
                        chipExpandido = if (chipExpandido == TipoCategoria.ANO) null else TipoCategoria.ANO
                    },
                    aoEscolherValor = { titulo ->
                        rolarParaTitulo(titulo)
                        chipExpandido = null
                    },
                )

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(estado.linhas, key = { it.titulo }) { linha ->
                        LinhaCarrosselView(
                            linha = linha,
                            aoClicarJogo = aoClicarJogo,
                            aoClicarVerTudo = aoClicarVerTudo,
                        )
                    }
                }
            }
        }
    }
}

/** Barra fixa no topo: chips "Meus jogos"/"Faltam" pulam direto; "Gênero"/"Ano" expandem
 * uma segunda linha de chips (empurra o conteúdo pra baixo, sem modal) com os valores
 * daquele tipo pra escolher exatamente pra qual linha pular. */
@Composable
private fun BarraDeIndice(
    linhas: List<LinhaCarrossel>,
    chipExpandido: TipoCategoria?,
    aoTocarMeusJogos: () -> Unit,
    aoTocarFaltam: () -> Unit,
    aoAlternarGenero: () -> Unit,
    aoAlternarAno: () -> Unit,
    aoEscolherValor: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            AssistChip(onClick = aoTocarMeusJogos, label = { Text("Meus jogos") }, modifier = Modifier.padding(end = 8.dp))
            AssistChip(onClick = aoTocarFaltam, label = { Text("Faltam") }, modifier = Modifier.padding(end = 8.dp))
            AssistChip(onClick = aoAlternarGenero, label = { Text("Gênero") }, modifier = Modifier.padding(end = 8.dp))
            AssistChip(onClick = aoAlternarAno, label = { Text("Ano") })
        }

        val titulosExpandidos = when (chipExpandido) {
            null -> emptyList()
            else -> linhas.filter { it.tipo == chipExpandido }.map { it.titulo }
        }
        if (titulosExpandidos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                titulosExpandidos.forEach { titulo ->
                    AssistChip(
                        onClick = { aoEscolherValor(titulo) },
                        label = { Text(titulo) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinhaCarrosselView(
    linha: LinhaCarrossel,
    aoClicarJogo: (Long) -> Unit,
    aoClicarVerTudo: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = linha.titulo,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(jogosVisiveis(linha), key = { it.jogo.id }) { jogoComPosse ->
                CartaoJogo(
                    jogoComPosse = jogoComPosse,
                    aoClicar = { aoClicarJogo(jogoComPosse.jogo.id) },
                )
            }
            if (mostrarVerTudo(linha)) {
                item(key = "${linha.titulo}:ver_tudo") {
                    CartaoVerTudo(aoClicar = { aoClicarVerTudo(linha.titulo) })
                }
            }
        }
    }
}

@Composable
private fun CartaoVerTudo(aoClicar: () -> Unit) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(3f / 4f)
            .padding(8.dp)
            .clickable(onClick = aoClicar),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Ver tudo", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CartaoJogo(jogoComPosse: JogoComPosse, aoClicar: () -> Unit) {
    Card(
        modifier = Modifier
            .width(120.dp)
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

/** Badge simples com a cor/rótulo do status de posse do jogo. Não é `private`: reaproveitado
 * por [com.thalys.catalogosnes.ui.biblioteca.TelaCategoriaCompleta] (mesmo pacote). */
@Composable
internal fun SeloStatus(status: StatusPosse, modifier: Modifier = Modifier) {
    val (rotulo, cor) = when (status) {
        StatusPosse.TENHO -> "Tenho" to SnesVerde
        StatusPosse.QUERO_TER -> "Quero ter" to SnesRoxoClaro
        StatusPosse.NAO_INTERESSA -> "Não interessa" to SnesVermelho
    }
    Box(
        modifier = modifier
            .background(color = cor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = rotulo, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewTelaBiblioteca() {
    CatalogoSnesTheme {
        TelaBiblioteca(aoClicarJogo = {}, aoClicarSincronizar = {}, aoClicarVerTudo = {})
    }
}
