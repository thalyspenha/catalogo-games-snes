package com.thalys.catalogosnes.ui.biblioteca

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.local.modeloCapa
import com.thalys.catalogosnes.data.model.StatusPosse
import com.thalys.catalogosnes.ui.theme.CatalogoSnesTheme
import com.thalys.catalogosnes.ui.theme.SnesRoxoClaro
import com.thalys.catalogosnes.ui.theme.SnesVerde
import com.thalys.catalogosnes.ui.theme.SnesVermelho
import kotlinx.coroutines.launch

private enum class SubmenuFiltro { GENERO, ANO }

/**
 * Biblioteca principal: grid único (4 colunas) com o catálogo completo, sempre visível ao
 * abrir o app (filtro default [FiltroBiblioteca.Todos]). Menu lateral
 * ([ModalNavigationDrawer], aberto pelo ícone de hambúrguer) aplica um [FiltroBiblioteca]
 * por vez sobre o grid; Gênero/Ano expandem submenu com os valores existentes no catálogo
 * atual, incluindo "Sem gênero"/"Sem ano". Busca por nome ignora o filtro ativo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaBiblioteca(
    aoClicarJogo: (Long) -> Unit,
    aoClicarSincronizar: () -> Unit,
    viewModel: BibliotecaViewModel = viewModel(
        factory = BibliotecaViewModel.Factory(LocalContext.current)
    ),
) {
    val estado by viewModel.estadoUi.collectAsStateWithLifecycle()
    val consultaBusca by viewModel.consultaBusca.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val escopo = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequesterBusca = remember { FocusRequester() }
    var buscaExpandida by rememberSaveable { mutableStateOf(false) }
    var submenuExpandido by remember { mutableStateOf<SubmenuFiltro?>(null) }

    fun fecharBusca() {
        buscaExpandida = false
        viewModel.aoMudarConsultaBusca("")
        focusManager.clearFocus()
    }

    fun selecionarFiltro(filtro: FiltroBiblioteca) {
        viewModel.aoSelecionarFiltro(filtro)
        submenuExpandido = null
        escopo.launch { drawerState.close() }
    }

    LaunchedEffect(buscaExpandida) {
        if (buscaExpandida) {
            focusRequesterBusca.requestFocus()
        }
    }

    BackHandler(enabled = buscaExpandida) {
        fecharBusca()
    }

    BackHandler(enabled = drawerState.isOpen) {
        escopo.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ConteudoMenuFiltro(
                    filtroSelecionado = estado.filtroSelecionado,
                    generosDisponiveis = estado.generosDisponiveis,
                    anosDisponiveis = estado.anosDisponiveis,
                    submenuExpandido = submenuExpandido,
                    aoAlternarSubmenu = { tipo ->
                        submenuExpandido = if (submenuExpandido == tipo) null else tipo
                    },
                    aoSelecionarFiltro = ::selecionarFiltro,
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { escopo.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir menu de filtro")
                        }
                    },
                    title = {
                        if (buscaExpandida) {
                            TextField(
                                value = consultaBusca,
                                onValueChange = viewModel::aoMudarConsultaBusca,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesterBusca),
                                singleLine = true,
                                placeholder = { Text("Buscar jogo") },
                            )
                        } else {
                            Text(estado.tituloTopBar)
                        }
                    },
                    actions = {
                        if (buscaExpandida) {
                            IconButton(onClick = { fecharBusca() }) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar busca")
                            }
                        } else {
                            IconButton(onClick = { buscaExpandida = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Buscar jogo")
                            }
                            IconButton(onClick = aoClicarSincronizar) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sincronizar catálogo")
                            }
                        }
                    },
                )
            }
        ) { paddingInterno ->
            val resultadoBusca = estado.resultadoBusca
            when {
                estado.carregando -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingInterno),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                resultadoBusca != null -> GridDeJogos(
                    jogos = resultadoBusca,
                    aoClicarJogo = aoClicarJogo,
                    mensagemVazia = "Nenhum jogo encontrado",
                    modifier = Modifier.padding(8.dp),
                    contentPadding = paddingInterno,
                )

                else -> GridDeJogos(
                    jogos = estado.jogosFiltrados,
                    aoClicarJogo = aoClicarJogo,
                    mensagemVazia = "Nenhum jogo encontrado",
                    modifier = Modifier.padding(8.dp),
                    contentPadding = paddingInterno,
                )
            }
        }
    }
}

/** Conteúdo do menu lateral: Todos/Tenho/Quero ter/Faltam pulam direto; Gênero/Ano expandem
 * submenu inline com os valores existentes no catálogo. Selecionar qualquer item aplica o
 * filtro e fecha o drawer (via [aoSelecionarFiltro], que já chama `drawerState.close()`). */
@Composable
private fun ConteudoMenuFiltro(
    filtroSelecionado: FiltroBiblioteca,
    generosDisponiveis: List<String>,
    anosDisponiveis: List<String>,
    submenuExpandido: SubmenuFiltro?,
    aoAlternarSubmenu: (SubmenuFiltro) -> Unit,
    aoSelecionarFiltro: (FiltroBiblioteca) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        NavigationDrawerItem(
            label = { Text("Todos") },
            selected = filtroSelecionado == FiltroBiblioteca.Todos,
            onClick = { aoSelecionarFiltro(FiltroBiblioteca.Todos) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Tenho") },
            selected = filtroSelecionado == FiltroBiblioteca.Tenho,
            onClick = { aoSelecionarFiltro(FiltroBiblioteca.Tenho) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Quero ter") },
            selected = filtroSelecionado == FiltroBiblioteca.QueroTer,
            onClick = { aoSelecionarFiltro(FiltroBiblioteca.QueroTer) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Faltam") },
            selected = filtroSelecionado == FiltroBiblioteca.Faltam,
            onClick = { aoSelecionarFiltro(FiltroBiblioteca.Faltam) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Gênero") },
            selected = false,
            onClick = { aoAlternarSubmenu(SubmenuFiltro.GENERO) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (submenuExpandido == SubmenuFiltro.GENERO) {
            generosDisponiveis.forEach { valor ->
                NavigationDrawerItem(
                    label = { Text(valor) },
                    selected = filtroSelecionado == FiltroBiblioteca.Genero(valor),
                    onClick = { aoSelecionarFiltro(FiltroBiblioteca.Genero(valor)) },
                    modifier = Modifier.padding(start = 28.dp, end = 12.dp),
                )
            }
        }
        NavigationDrawerItem(
            label = { Text("Ano") },
            selected = false,
            onClick = { aoAlternarSubmenu(SubmenuFiltro.ANO) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (submenuExpandido == SubmenuFiltro.ANO) {
            anosDisponiveis.forEach { valor ->
                NavigationDrawerItem(
                    label = { Text(valor) },
                    selected = filtroSelecionado == FiltroBiblioteca.Ano(valor),
                    onClick = { aoSelecionarFiltro(FiltroBiblioteca.Ano(valor)) },
                    modifier = Modifier.padding(start = 28.dp, end = 12.dp),
                )
            }
        }
    }
}

/**
 * Grid de 4 colunas com estado vazio embutido — usado pelo grid principal da biblioteca e
 * pelo resultado de busca.
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
            columns = GridCells.Fixed(4),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier,
        ) {
            items(jogos, key = { it.jogo.id }) { jogoComPosse ->
                CartaoJogo(
                    jogoComPosse = jogoComPosse,
                    aoClicar = { aoClicarJogo(jogoComPosse.jogo.id) },
                )
            }
        }
    }
}

/** Nome ganha `minLines = 2` pra reservar sempre a altura de 2 linhas, nome curto ou longo —
 * padroniza a altura do card independente do tamanho do nome. */
@Composable
private fun CartaoJogo(jogoComPosse: JogoComPosse, aoClicar: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = aoClicar)) {
        Box {
            AsyncImage(
                model = jogoComPosse.jogo.modeloCapa(),
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
        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                text = jogoComPosse.jogo.nome,
                style = MaterialTheme.typography.bodySmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Badge simples com a cor/rótulo do status de posse do jogo. */
@Composable
private fun SeloStatus(status: StatusPosse, modifier: Modifier = Modifier) {
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
        TelaBiblioteca(aoClicarJogo = {}, aoClicarSincronizar = {})
    }
}
