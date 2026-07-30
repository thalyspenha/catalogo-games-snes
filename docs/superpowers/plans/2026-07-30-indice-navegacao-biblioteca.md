# Índice de navegação e "ver tudo" na biblioteca — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir o gap de navegação da biblioteca — linha "Faltam" com ~1700 cards sem paginação e bloco "Ano" enterrado atrás de dezenas de linhas de "Gênero" — com um cap de 20 jogos por linha + tela "Ver tudo" por categoria, e uma barra de chips no topo que pula direto pra qualquer linha (com sub-lista expansível pra escolher gênero/ano específico).

**Architecture:** `LinhaCarrossel` ganha um campo `tipo: TipoCategoria`; duas funções puras novas (`mostrarVerTudo`, `jogosVisiveis`) decidem o corte de 20 por linha. `TelaBiblioteca` ganha uma barra de chips (scroll-to via `LazyListState.animateScrollToItem`) e cada linha corta em 20 + card "Ver tudo" que navega pra uma tela nova, `TelaCategoriaCompleta`, que reaproveita o grid de 3 colunas que existia antes dos carrosséis.

**Tech Stack:** Kotlin 2.0.21 + Jetpack Compose (Material3, Compose BOM 2024.10.01), Navigation Compose (rota nova `categoria/{titulo}`), JUnit 4.13.2 pra teste puro.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 35 — não mudar.
- Sem DI framework — Factory manual via `ViewModelProvider.Factory`, `context.applicationContext`, mesmo padrão de `BibliotecaViewModel`/`DetalheJogoViewModel`.
- Sem teste instrumentado/Compose no projeto — cobertura automatizada é só de lógica pura (JUnit). UI verificada por compilação + checagem visual em device (ainda pendente de rodada anterior, não é bloqueio pra este plano).
- Pacote base `com.thalys.catalogosnes`. Testes ficam em `app/src/test/java/com/thalys/catalogosnes/<mesmo-pacote-do-main>/`.
- Compose BOM 2024.10.01 (`gradle/libs.versions.toml:7`) — `AssistChip` e `Icons.AutoMirrored.Filled.ArrowBack` já disponíveis nessa versão, sem upgrade de dependência necessário.
- Cap de **20 jogos visíveis por linha**, igual pras 4 categorias (Meus jogos, Faltam, cada linha de Gênero, cada linha de Ano) — valor exato da spec, não é config ajustável pelo usuário.
- Ordem das linhas na `LazyColumn` não muda: Meus jogos → Faltam → Gêneros (A-Z + Sem gênero) → Anos (cronológico + Sem ano). Este plano não reordena nada, só corta e indexa.
- Navegação existente não pode quebrar: `detalhe/{jogoId}` e `sincronizacao` continuam funcionando exatamente como antes.

---

## File Structure

- **Modify:** `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt` — `LinhaCarrossel` ganha `tipo: TipoCategoria`; novo enum `TipoCategoria`; duas funções puras novas, `mostrarVerTudo()` e `jogosVisiveis()`.
- **Modify:** `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt` — testes novos apensados aos 10 já existentes (que continuam válidos sem alteração).
- **Modify:** `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt` — barra de chips (`BarraDeIndice`), `LazyListState` + scroll-to, corte de 20 por linha + card "Ver tudo", novo parâmetro `aoClicarVerTudo: (String) -> Unit`. `SeloStatus` vira `internal` (deixa de ser `private`) pra ser reaproveitado por `TelaCategoriaCompleta.kt`.
- **Create:** `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/CategoriaCompletaViewModel.kt` — observa a biblioteca, roda `montarCarrosseis()` de novo, filtra pra a linha com o título pedido.
- **Create:** `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaCategoriaCompleta.kt` — grid de 3 colunas (mesmo padrão do grid antigo, pré-carrosséis), reaproveita `SeloStatus` de `TelaBiblioteca.kt`.
- **Modify:** `app/src/main/java/com/thalys/catalogosnes/ui/navigation/CatalogoNavHost.kt` — nova rota `categoria/{titulo}` (`Uri.encode`/`Uri.decode`), wiring do novo parâmetro `aoClicarVerTudo` de `TelaBiblioteca`.

---

### Task 1: `TipoCategoria` + `mostrarVerTudo` + `jogosVisiveis`

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt`
- Modify: `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt`

**Interfaces:**
- Consumes: nada novo — mesma base de `JogoComPosse`/`StatusPosse` já usada.
- Produces: `enum class TipoCategoria { MEUS_JOGOS, FALTAM, GENERO, ANO }`; `LinhaCarrossel(titulo: String, jogos: List<JogoComPosse>, tipo: TipoCategoria)` (ganhou um campo, ordem posicional preservada pros dois primeiros); `fun mostrarVerTudo(linha: LinhaCarrossel, cap: Int = 20): Boolean`; `fun jogosVisiveis(linha: LinhaCarrossel, cap: Int = 20): List<JogoComPosse>`. Usados pela Task 2 (`TelaBiblioteca`).
- Nenhum outro arquivo do projeto constrói `LinhaCarrossel(...)` diretamente (só `montarCarrosseis()` faz isso) — adicionar um campo obrigatório não quebra nenhum consumidor existente.

- [ ] **Step 1: Escrever os testes novos (falhando)**

Apensar estes testes à classe `MontadorCarrosseisBibliotecaTest` já existente (depois do último teste, `lista vazia produz nenhuma linha`), sem alterar os 10 testes que já estão lá:

```kotlin
    @Test
    fun `linha de meus jogos tem tipo MEUS_JOGOS`() {
        val jogos = listOf(JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.TENHO)))
        val linhas = montarCarrosseis(jogos)
        assertEquals(TipoCategoria.MEUS_JOGOS, linhas.first { it.titulo == "Meus jogos" }.tipo)
    }

    @Test
    fun `linha de faltam tem tipo FALTAM`() {
        val jogos = listOf(JogoComPosse(jogo(1, "A"), null))
        val linhas = montarCarrosseis(jogos)
        assertEquals(TipoCategoria.FALTAM, linhas.first { it.titulo == "Faltam" }.tipo)
    }

    @Test
    fun `linhas de genero e sem genero tem tipo GENERO`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG"), null),
            JogoComPosse(jogo(2, "B", genero = null), null),
        )
        val linhas = montarCarrosseis(jogos)
        assertEquals(TipoCategoria.GENERO, linhas.first { it.titulo == "RPG" }.tipo)
        assertEquals(TipoCategoria.GENERO, linhas.first { it.titulo == "Sem gênero" }.tipo)
    }

    @Test
    fun `linhas de ano e sem ano tem tipo ANO`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", ano = 1994), null),
            JogoComPosse(jogo(2, "B", ano = null), null),
        )
        val linhas = montarCarrosseis(jogos)
        assertEquals(TipoCategoria.ANO, linhas.first { it.titulo == "1994" }.tipo)
        assertEquals(TipoCategoria.ANO, linhas.first { it.titulo == "Sem ano" }.tipo)
    }

    @Test
    fun `mostrarVerTudo false com 20 jogos ou menos`() {
        val linha = LinhaCarrossel(
            titulo = "X",
            jogos = List(20) { i -> JogoComPosse(jogo(i.toLong(), "J$i"), null) },
            tipo = TipoCategoria.GENERO,
        )
        assertEquals(false, mostrarVerTudo(linha))
    }

    @Test
    fun `mostrarVerTudo true com mais de 20 jogos`() {
        val linha = LinhaCarrossel(
            titulo = "X",
            jogos = List(21) { i -> JogoComPosse(jogo(i.toLong(), "J$i"), null) },
            tipo = TipoCategoria.GENERO,
        )
        assertEquals(true, mostrarVerTudo(linha))
    }

    @Test
    fun `jogosVisiveis retorna lista inteira quando dentro do cap`() {
        val jogos = List(15) { i -> JogoComPosse(jogo(i.toLong(), "J$i"), null) }
        val linha = LinhaCarrossel(titulo = "X", jogos = jogos, tipo = TipoCategoria.GENERO)
        assertEquals(15, jogosVisiveis(linha).size)
    }

    @Test
    fun `jogosVisiveis corta nos primeiros 20 quando passa do cap`() {
        val jogos = List(25) { i -> JogoComPosse(jogo(i.toLong(), "J$i"), null) }
        val linha = LinhaCarrossel(titulo = "X", jogos = jogos, tipo = TipoCategoria.GENERO)
        val visiveis = jogosVisiveis(linha)
        assertEquals(20, visiveis.size)
        assertEquals((0..19).map { it.toLong() }, visiveis.map { it.jogo.id })
    }
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.MontadorCarrosseisBibliotecaTest"`
Expected: FAIL — `TipoCategoria`/`mostrarVerTudo`/`jogosVisiveis` não existem ainda, e `LinhaCarrossel(...)` com 2 args (usado no `montarCarrosseis` atual) não bate com as chamadas de 3 args dos testes novos (erro de compilação).

- [ ] **Step 3: Implementar `TipoCategoria`, `tipo` em `LinhaCarrossel`, `mostrarVerTudo`, `jogosVisiveis`**

Conteúdo completo de `MontadorCarrosseisBiblioteca.kt` depois da mudança:

```kotlin
package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.model.StatusPosse

enum class TipoCategoria { MEUS_JOGOS, FALTAM, GENERO, ANO }

/** Uma linha de carrossel na biblioteca: título da categoria + jogos que pertencem a ela. */
data class LinhaCarrossel(
    val titulo: String,
    val jogos: List<JogoComPosse>,
    val tipo: TipoCategoria,
)

private const val TITULO_MEUS_JOGOS = "Meus jogos"
private const val TITULO_FALTAM = "Faltam"
private const val TITULO_SEM_GENERO = "Sem gênero"
private const val TITULO_SEM_ANO = "Sem ano"
private const val CAP_PADRAO_POR_LINHA = 20

/**
 * Agrupa a biblioteca completa em linhas de carrossel, na ordem:
 * Meus jogos -> Faltam -> Gêneros (A-Z, "Sem gênero" no fim) -> Anos (cronológico, "Sem ano" no fim).
 * Categoria sem nenhum jogo não gera linha.
 */
fun montarCarrosseis(jogos: List<JogoComPosse>): List<LinhaCarrossel> {
    val linhas = mutableListOf<LinhaCarrossel>()

    val meusJogos = jogos.filter { it.posse?.status == StatusPosse.TENHO }
    if (meusJogos.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_MEUS_JOGOS, meusJogos, TipoCategoria.MEUS_JOGOS)
    }

    val faltam = jogos.filter {
        it.posse?.status != StatusPosse.TENHO && it.posse?.status != StatusPosse.NAO_INTERESSA
    }
    if (faltam.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_FALTAM, faltam, TipoCategoria.FALTAM)
    }

    val porGenero = jogos.groupBy { it.jogo.genero }
    porGenero.keys.filterNotNull().sorted().forEach { genero ->
        linhas += LinhaCarrossel(genero, porGenero.getValue(genero), TipoCategoria.GENERO)
    }
    porGenero[null]?.let { semGenero ->
        linhas += LinhaCarrossel(TITULO_SEM_GENERO, semGenero, TipoCategoria.GENERO)
    }

    val porAno = jogos.groupBy { it.jogo.anoLancamento }
    porAno.keys.filterNotNull().sorted().forEach { ano ->
        linhas += LinhaCarrossel(ano.toString(), porAno.getValue(ano), TipoCategoria.ANO)
    }
    porAno[null]?.let { semAno ->
        linhas += LinhaCarrossel(TITULO_SEM_ANO, semAno, TipoCategoria.ANO)
    }

    return linhas
}

/** true quando a linha tem mais jogos que [cap] — sinal pra UI mostrar o card "Ver tudo". */
fun mostrarVerTudo(linha: LinhaCarrossel, cap: Int = CAP_PADRAO_POR_LINHA): Boolean =
    linha.jogos.size > cap

/** Jogos a exibir na linha: a lista inteira se estiver dentro do [cap], senão só os primeiros [cap]. */
fun jogosVisiveis(linha: LinhaCarrossel, cap: Int = CAP_PADRAO_POR_LINHA): List<JogoComPosse> =
    if (linha.jogos.size <= cap) linha.jogos else linha.jogos.take(cap)
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.MontadorCarrosseisBibliotecaTest"`
Expected: PASS — 18 testes (10 antigos + 8 novos), 0 falhas.

- [ ] **Step 5: Confirmar que o resto do projeto continua compilando**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — nenhum outro arquivo constrói `LinhaCarrossel(...)` diretamente, então o campo novo não quebra nada.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt
git commit -m "feat: LinhaCarrossel ganha tipo + mostrarVerTudo/jogosVisiveis (cap de 20)"
```

---

### Task 2: `TelaBiblioteca` — barra de chips + cap/"Ver tudo"

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt`

**Interfaces:**
- Consumes: `TipoCategoria`, `LinhaCarrossel.tipo`, `mostrarVerTudo(linha, cap=20)`, `jogosVisiveis(linha, cap=20)` (Task 1).
- Produces: `TelaBiblioteca(aoClicarJogo: (Long) -> Unit, aoClicarSincronizar: () -> Unit, aoClicarVerTudo: (String) -> Unit, viewModel: BibliotecaViewModel = ...)` — o parâmetro novo `aoClicarVerTudo` precisa ser passado por quem chama `TelaBiblioteca` (Task 4, `CatalogoNavHost`); `internal fun SeloStatus(status: StatusPosse, modifier: Modifier = Modifier)` — reaproveitado pela Task 3 (`TelaCategoriaCompleta`, mesmo pacote `ui.biblioteca`).
- **Efeito colateral esperado:** depois desta task, `./gradlew :app:compileDebugKotlin` FALHA em `CatalogoNavHost.kt` (chamada a `TelaBiblioteca(...)` sem o 3º argumento obrigatório). Isso é esperado — a Task 4 corrige.

- [ ] **Step 1: Reescrever `TelaBiblioteca.kt`**

Conteúdo completo do arquivo depois da mudança:

```kotlin
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
```

- [ ] **Step 2: Compilar e confirmar que a falha é só em `CatalogoNavHost.kt`**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: FAIL — erro em `CatalogoNavHost.kt` (`TelaBiblioteca(...)` sem o argumento `aoClicarVerTudo`). Esse erro é esperado nesta etapa; a Task 4 corrige. Nenhum outro erro deve aparecer (em particular, `TelaBiblioteca.kt` em si deve compilar limpo).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt
git commit -m "feat: TelaBiblioteca — barra de chips (índice) e cap de 20 por linha com Ver tudo"
```

---

### Task 3: `TelaCategoriaCompleta` (grid completo de uma categoria)

**Files:**
- Create: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/CategoriaCompletaViewModel.kt`
- Create: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaCategoriaCompleta.kt`

**Interfaces:**
- Consumes: `montarCarrosseis(jogos): List<LinhaCarrossel>` (Task 1, já existente), `JogoRepository.observarBiblioteca()` (já existente, inalterado), `internal fun SeloStatus(status, modifier)` (Task 2, `TelaBiblioteca.kt`, mesmo pacote — sem necessidade de import).
- Produces: `TelaCategoriaCompleta(titulo: String, aoClicarJogo: (Long) -> Unit, aoVoltar: () -> Unit, viewModel: CategoriaCompletaViewModel = ...)` — usado pela Task 4 (`CatalogoNavHost`).
- Esta task não conserta a falha de compilação deixada pela Task 2 (isso é escopo da Task 4) — a verificação aqui é que os dois arquivos novos, em si, não introduzem nenhum erro adicional além do já esperado.

- [ ] **Step 1: Criar `CategoriaCompletaViewModel.kt`**

```kotlin
package com.thalys.catalogosnes.ui.biblioteca

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.repository.JogoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Estado exibido pela [com.thalys.catalogosnes.ui.biblioteca.TelaCategoriaCompleta]. */
data class CategoriaCompletaUiState(
    val titulo: String,
    val jogos: List<JogoComPosse> = emptyList(),
    val carregando: Boolean = true,
)

/**
 * Observa a biblioteca completa, reagrupa via [montarCarrosseis] e expõe só os jogos da
 * linha cujo título bate com [titulo] — usado pela tela "Ver tudo" de uma categoria. Reaproveita
 * a função de agrupamento já testada em vez de duplicar lógica de filtro.
 */
class CategoriaCompletaViewModel(
    repository: JogoRepository,
    titulo: String,
) : ViewModel() {

    val estadoUi: StateFlow<CategoriaCompletaUiState> = repository.observarBiblioteca()
        .map { jogos ->
            val jogosDaLinha = montarCarrosseis(jogos).firstOrNull { it.titulo == titulo }?.jogos ?: emptyList()
            CategoriaCompletaUiState(titulo = titulo, jogos = jogosDaLinha, carregando = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriaCompletaUiState(titulo = titulo),
        )

    /** Factory manual, no mesmo espírito de [BibliotecaViewModel.Factory] — aqui com um
     * argumento extra ([titulo]) além do context, mesmo padrão de `DetalheJogoViewModel.Factory`. */
    class Factory(
        private val context: Context,
        private val titulo: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = JogoRepository.obterInstancia(context.applicationContext)
            return CategoriaCompletaViewModel(repository, titulo) as T
        }
    }
}
```

- [ ] **Step 2: Criar `TelaCategoriaCompleta.kt`**

```kotlin
package com.thalys.catalogosnes.ui.biblioteca

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = paddingInterno,
            modifier = Modifier.padding(8.dp),
        ) {
            items(estado.jogos, key = { it.jogo.id }) { jogoComPosse ->
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
```

- [ ] **Step 3: Compilar e confirmar que não há erro novo além do já esperado**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: FAIL, mas com **exatamente o mesmo erro** da Task 2 (`CatalogoNavHost.kt`, chamada a `TelaBiblioteca(...)` faltando `aoClicarVerTudo`) — nenhum erro em `CategoriaCompletaViewModel.kt` ou `TelaCategoriaCompleta.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/CategoriaCompletaViewModel.kt app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaCategoriaCompleta.kt
git commit -m "feat: TelaCategoriaCompleta — grid de 3 colunas pra 'Ver tudo' de uma categoria"
```

---

### Task 4: `CatalogoNavHost` — nova rota + wiring final

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/navigation/CatalogoNavHost.kt`

**Interfaces:**
- Consumes: `TelaBiblioteca(aoClicarJogo, aoClicarSincronizar, aoClicarVerTudo)` (Task 2), `TelaCategoriaCompleta(titulo, aoClicarJogo, aoVoltar)` (Task 3).
- Produces: nada consumido por task posterior — última task do plano.

- [ ] **Step 1: Reescrever `CatalogoNavHost.kt`**

Conteúdo completo do arquivo depois da mudança:

```kotlin
package com.thalys.catalogosnes.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca
import com.thalys.catalogosnes.ui.biblioteca.TelaCategoriaCompleta
import com.thalys.catalogosnes.ui.detalhe.TelaDetalheJogo
import com.thalys.catalogosnes.ui.sincronizacao.TelaSincronizacao

private const val ROTA_BIBLIOTECA = "biblioteca"
private const val ARGUMENTO_JOGO_ID = "jogoId"
private const val ROTA_DETALHE = "detalhe/{$ARGUMENTO_JOGO_ID}"
private const val ROTA_SINCRONIZACAO = "sincronizacao"
private const val ARGUMENTO_TITULO_CATEGORIA = "titulo"
private const val ROTA_CATEGORIA = "categoria/{$ARGUMENTO_TITULO_CATEGORIA}"

/**
 * Grafo de navegação do app: biblioteca (carrosséis por categoria) -> detalhe/edição de
 * posse de um jogo, ou -> grid completo de uma categoria (card "Ver tudo").
 */
@Composable
fun CatalogoNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROTA_BIBLIOTECA) {
        composable(ROTA_BIBLIOTECA) {
            TelaBiblioteca(
                aoClicarJogo = { jogoId -> navController.navigate("detalhe/$jogoId") },
                aoClicarSincronizar = { navController.navigate(ROTA_SINCRONIZACAO) },
                aoClicarVerTudo = { titulo -> navController.navigate("categoria/${Uri.encode(titulo)}") },
            )
        }
        composable(
            route = ROTA_DETALHE,
            arguments = listOf(navArgument(ARGUMENTO_JOGO_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val jogoId = backStackEntry.arguments?.getLong(ARGUMENTO_JOGO_ID) ?: return@composable
            TelaDetalheJogo(
                jogoId = jogoId,
                aoVoltar = { navController.popBackStack() },
            )
        }
        composable(ROTA_SINCRONIZACAO) {
            TelaSincronizacao(aoVoltar = { navController.popBackStack() })
        }
        composable(
            route = ROTA_CATEGORIA,
            arguments = listOf(navArgument(ARGUMENTO_TITULO_CATEGORIA) { type = NavType.StringType }),
        ) { backStackEntry ->
            val tituloCodificado = backStackEntry.arguments?.getString(ARGUMENTO_TITULO_CATEGORIA)
                ?: return@composable
            TelaCategoriaCompleta(
                titulo = Uri.decode(tituloCodificado),
                aoClicarJogo = { jogoId -> navController.navigate("detalhe/$jogoId") },
                aoVoltar = { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 2: Compilar e confirmar que o módulo `:app` builda limpo**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, sem erros — a falha intencional das Tasks 2/3 está resolvida.

- [ ] **Step 3: Rodar a suíte de testes completa**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test`
Expected: BUILD SUCCESSFUL, todos os testes verdes (os pré-existentes + os 18 de `MontadorCarrosseisBibliotecaTest`, 10 antigos + 8 novos).

- [ ] **Step 4: Verificação manual (recomendado)**

Rodar o app num emulador/device e checar: barra de chips aparece no topo da biblioteca; tocar "Meus jogos"/"Faltam" rola até a linha; tocar "Gênero"/"Ano" expande a segunda linha de chips com os valores, e tocar um valor rola até a linha exata e recolhe a segunda linha; linha com mais de 20 jogos mostra o card "Ver tudo" no final, que abre o grid de 3 colunas só daquela categoria, com botão de voltar funcionando. Sem teste automatizado de Compose no projeto — essa checagem é visual, mesma ressalva das tasks anteriores.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/navigation/CatalogoNavHost.kt
git commit -m "feat: CatalogoNavHost — rota categoria/{titulo} e wiring do card Ver tudo"
```
