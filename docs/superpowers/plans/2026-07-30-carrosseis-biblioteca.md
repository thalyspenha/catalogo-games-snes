# Carrosséis por categoria na biblioteca — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar o grid único da `TelaBiblioteca` por carrosséis horizontais agrupados em 4 categorias (Meus jogos, Faltam, Gênero, Ano), reaproveitando o dado já observado por `JogoRepository.observarBiblioteca()`.

**Architecture:** Uma função pura nova (`montarCarrosseis`) transforma `List<JogoComPosse>` em `List<LinhaCarrossel>` ordenada; `BibliotecaViewModel` passa a expor essa lista em vez da lista plana; `TelaBiblioteca` troca `LazyVerticalGrid` por `LazyColumn` de linhas, cada linha com título + `LazyRow` do card já existente. `JogoRepository` e o schema Room não mudam.

**Tech Stack:** Kotlin 2.0.21 + Jetpack Compose (Material3), Room 2.6.1 (inalterado), JUnit 4.13.2 pra teste puro.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 35 (`app/build.gradle.kts`) — não mudar.
- Sem DI framework (Hilt/Dagger/Koin) — seguir padrão manual `companion object.obterInstancia(context)` / `ViewModelProvider.Factory` já usado.
- Sem teste instrumentado/Compose no projeto (nenhum existe hoje) — cobertura automatizada é só de lógica pura (JUnit), igual `CalculoRestanteTest`. Verificação da UI é por compilação + checagem visual, não teste automatizado de Compose.
- Pacote base `com.thalys.catalogosnes`. Testes ficam em `app/src/test/java/com/thalys/catalogosnes/<mesmo-pacote-do-main>/`.
- Navegação pro detalhe não muda: `navController.navigate("detalhe/$jogoId")`, disparado via callback `aoClicarJogo: (Long) -> Unit`.

---

## File Structure

- **Create:** `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt` — `LinhaCarrossel` (data class) + `montarCarrosseis()` (função pura de agrupamento/ordenação).
- **Create:** `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt` — testes unitários da função acima.
- **Modify:** `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/BibliotecaViewModel.kt` — `BibliotecaUiState` passa a carregar `linhas: List<LinhaCarrossel>` em vez de `jogos: List<JogoComPosse>`.
- **Modify:** `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt` — `LazyVerticalGrid` vira `LazyColumn` de linhas; `CartaoJogo` ganha largura fixa (hoje depende da coluna do grid).

---

### Task 1: `LinhaCarrossel` + `montarCarrosseis` (agrupamento puro)

**Files:**
- Create: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt`
- Test: `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt`

**Interfaces:**
- Consumes: `JogoComPosse` (`data/local/JogoComPosse.kt`, campos `jogo: JogoEntity`, `posse: PosseUsuarioEntity?`), `JogoEntity` (campos `id: Long, nome: String, anoLancamento: Int?, genero: String?`), `StatusPosse` (`TENHO`, `QUERO_TER`, `NAO_INTERESSA`).
- Produces: `data class LinhaCarrossel(val titulo: String, val jogos: List<JogoComPosse>)` e `fun montarCarrosseis(jogos: List<JogoComPosse>): List<LinhaCarrossel>` — usados por `BibliotecaViewModel` na Task 2.

- [ ] **Step 1: Escrever os testes (falhando)**

```kotlin
package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.local.JogoEntity
import com.thalys.catalogosnes.data.local.PosseUsuarioEntity
import com.thalys.catalogosnes.data.model.StatusPosse
import org.junit.Assert.assertEquals
import org.junit.Test

class MontadorCarrosseisBibliotecaTest {

    private fun jogo(
        id: Long,
        nome: String,
        genero: String? = "Ação",
        ano: Int? = 1994,
    ) = JogoEntity(
        id = id,
        nome = nome,
        descricao = null,
        anoLancamento = ano,
        genero = genero,
        desenvolvedora = null,
        publicadora = null,
        urlCapa = null,
        regiao = null,
    )

    private fun posse(jogoId: Long, status: StatusPosse) = PosseUsuarioEntity(
        jogoId = jogoId,
        status = status,
        atualizadoEm = 0L,
    )

    @Test
    fun `ordem das categorias e meus jogos, faltam, generos, anos`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG", ano = 1994), posse(1, StatusPosse.TENHO)),
            JogoComPosse(jogo(2, "B", genero = "Ação", ano = 1995), null),
        )

        val linhas = montarCarrosseis(jogos)

        assertEquals(
            listOf("Meus jogos", "Faltam", "Ação", "RPG", "1994", "1995"),
            linhas.map { it.titulo },
        )
    }

    @Test
    fun `generos ficam em ordem alfabetica`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG"), null),
            JogoComPosse(jogo(2, "B", genero = "Ação"), null),
            JogoComPosse(jogo(3, "C", genero = "Luta"), null),
        )

        val linhas = montarCarrosseis(jogos)
        val titulosGenero = linhas.map { it.titulo }.filter { it in listOf("RPG", "Ação", "Luta") }

        assertEquals(listOf("Ação", "Luta", "RPG"), titulosGenero)
    }

    @Test
    fun `anos ficam em ordem cronologica`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", ano = 1996), null),
            JogoComPosse(jogo(2, "B", ano = 1992), null),
            JogoComPosse(jogo(3, "C", ano = 1994), null),
        )

        val linhas = montarCarrosseis(jogos)
        val titulosAno = linhas.map { it.titulo }.filter { it in listOf("1996", "1992", "1994") }

        assertEquals(listOf("1992", "1994", "1996"), titulosAno)
    }

    @Test
    fun `jogo sem genero cai em sem genero, no fim do bloco de generos`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG"), null),
            JogoComPosse(jogo(2, "B", genero = null), null),
        )

        val linhas = montarCarrosseis(jogos)
        val linhaSemGenero = linhas.first { it.titulo == "Sem gênero" }

        assertEquals(listOf(2L), linhaSemGenero.jogos.map { it.jogo.id })
        assertEquals(listOf("RPG", "Sem gênero"), linhas.map { it.titulo }.filter { it == "RPG" || it == "Sem gênero" })
    }

    @Test
    fun `jogo sem ano cai em sem ano, no fim do bloco de anos`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", ano = 1994), null),
            JogoComPosse(jogo(2, "B", ano = null), null),
        )

        val linhas = montarCarrosseis(jogos)
        val linhaSemAno = linhas.first { it.titulo == "Sem ano" }

        assertEquals(listOf(2L), linhaSemAno.jogos.map { it.jogo.id })
        assertEquals(listOf("1994", "Sem ano"), linhas.map { it.titulo }.filter { it == "1994" || it == "Sem ano" })
    }

    @Test
    fun `nao interessa nao aparece em faltam`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.NAO_INTERESSA)),
        )

        val linhas = montarCarrosseis(jogos)

        assertEquals(null, linhas.firstOrNull { it.titulo == "Faltam" })
    }

    @Test
    fun `jogo com status tenho nao aparece em faltam`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.TENHO)),
        )

        val linhas = montarCarrosseis(jogos)
        val faltam = linhas.firstOrNull { it.titulo == "Faltam" }

        assertEquals(null, faltam)
    }

    @Test
    fun `sem posse registrada conta como faltam`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), null),
        )

        val linhas = montarCarrosseis(jogos)
        val faltam = linhas.first { it.titulo == "Faltam" }

        assertEquals(listOf(1L), faltam.jogos.map { it.jogo.id })
    }

    @Test
    fun `categoria sem jogos nao gera linha`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.NAO_INTERESSA)),
        )

        val linhas = montarCarrosseis(jogos)

        assertEquals(false, linhas.any { it.jogos.isEmpty() })
    }

    @Test
    fun `lista vazia produz nenhuma linha`() {
        val linhas = montarCarrosseis(emptyList())

        assertEquals(emptyList<LinhaCarrossel>(), linhas)
    }
}
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.MontadorCarrosseisBibliotecaTest"`
Expected: FAIL — `LinhaCarrossel`/`montarCarrosseis` não existem ainda (erro de compilação).

- [ ] **Step 3: Implementar `LinhaCarrossel` + `montarCarrosseis`**

```kotlin
package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.model.StatusPosse

/** Uma linha de carrossel na biblioteca: título da categoria + jogos que pertencem a ela. */
data class LinhaCarrossel(
    val titulo: String,
    val jogos: List<JogoComPosse>,
)

private const val TITULO_MEUS_JOGOS = "Meus jogos"
private const val TITULO_FALTAM = "Faltam"
private const val TITULO_SEM_GENERO = "Sem gênero"
private const val TITULO_SEM_ANO = "Sem ano"

/**
 * Agrupa a biblioteca completa em linhas de carrossel, na ordem:
 * Meus jogos -> Faltam -> Gêneros (A-Z, "Sem gênero" no fim) -> Anos (cronológico, "Sem ano" no fim).
 * Categoria sem nenhum jogo não gera linha.
 */
fun montarCarrosseis(jogos: List<JogoComPosse>): List<LinhaCarrossel> {
    val linhas = mutableListOf<LinhaCarrossel>()

    val meusJogos = jogos.filter { it.posse?.status == StatusPosse.TENHO }
    if (meusJogos.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_MEUS_JOGOS, meusJogos)
    }

    val faltam = jogos.filter {
        it.posse?.status != StatusPosse.TENHO && it.posse?.status != StatusPosse.NAO_INTERESSA
    }
    if (faltam.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_FALTAM, faltam)
    }

    val porGenero = jogos.groupBy { it.jogo.genero }
    porGenero.keys.filterNotNull().sorted().forEach { genero ->
        linhas += LinhaCarrossel(genero, porGenero.getValue(genero))
    }
    porGenero[null]?.let { semGenero ->
        linhas += LinhaCarrossel(TITULO_SEM_GENERO, semGenero)
    }

    val porAno = jogos.groupBy { it.jogo.anoLancamento }
    porAno.keys.filterNotNull().sorted().forEach { ano ->
        linhas += LinhaCarrossel(ano.toString(), porAno.getValue(ano))
    }
    porAno[null]?.let { semAno ->
        linhas += LinhaCarrossel(TITULO_SEM_ANO, semAno)
    }

    return linhas
}
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.MontadorCarrosseisBibliotecaTest"`
Expected: PASS — 10 testes, 0 falhas.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt
git commit -m "feat: MontadorCarrosseisBiblioteca — agrupa biblioteca em linhas por categoria"
```

---

### Task 2: `BibliotecaViewModel` expõe `List<LinhaCarrossel>`

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/BibliotecaViewModel.kt`

**Interfaces:**
- Consumes: `montarCarrosseis(jogos: List<JogoComPosse>): List<LinhaCarrossel>` (Task 1), `JogoRepository.observarBiblioteca(): Flow<List<JogoComPosse>>` (já existe, inalterado).
- Produces: `data class BibliotecaUiState(val linhas: List<LinhaCarrossel> = emptyList(), val carregando: Boolean = true)` e `BibliotecaViewModel.estadoUi: StateFlow<BibliotecaUiState>` — consumidos por `TelaBiblioteca` na Task 3.

- [ ] **Step 1: Trocar `jogos` por `linhas` no state e no `map`**

Conteúdo completo de `BibliotecaViewModel.kt` depois da mudança:

```kotlin
package com.thalys.catalogosnes.ui.biblioteca

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thalys.catalogosnes.data.repository.JogoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Estado exibido pela [com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca]. */
data class BibliotecaUiState(
    val linhas: List<LinhaCarrossel> = emptyList(),
    val carregando: Boolean = true,
)

/**
 * Observa a biblioteca completa (jogo + posse do usuário) via [JogoRepository], agrupa em
 * carrosséis por categoria via [montarCarrosseis] e expõe como [StateFlow] para a UI.
 */
class BibliotecaViewModel(
    repository: JogoRepository,
) : ViewModel() {

    val estadoUi: StateFlow<BibliotecaUiState> = repository.observarBiblioteca()
        .map { jogos -> BibliotecaUiState(linhas = montarCarrosseis(jogos), carregando = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BibliotecaUiState(),
        )

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
```

(Removida a import de `JogoComPosse`, que não é mais usada diretamente neste arquivo.)

- [ ] **Step 2: Compilar e confirmar que só falha em `TelaBiblioteca.kt` (consumidor ainda não ajustado)**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: FAIL — erro em `TelaBiblioteca.kt` (`estado.jogos` não existe mais em `BibliotecaUiState`). Esse erro é esperado nesta etapa; a Task 3 resolve.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/BibliotecaViewModel.kt
git commit -m "feat: BibliotecaViewModel expõe linhas de carrossel em vez de lista plana"
```

---

### Task 3: `TelaBiblioteca` renderiza carrosséis

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt`

**Interfaces:**
- Consumes: `BibliotecaUiState.linhas: List<LinhaCarrossel>` (Task 2), `LinhaCarrossel(titulo: String, jogos: List<JogoComPosse>)` (Task 1).
- Produces: nada consumido por tarefa posterior — esta é a última tarefa do plano.

- [ ] **Step 1: Reescrever `TelaBiblioteca.kt`**

Conteúdo completo do arquivo depois da mudança:

```kotlin
package com.thalys.catalogosnes.ui.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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

/**
 * Biblioteca principal (estilo Netflix): carrosséis horizontais por categoria
 * (Meus jogos, Faltam, Gênero, Ano), montados por [montarCarrosseis].
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

            else -> LazyColumn(
                contentPadding = paddingInterno,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(estado.linhas, key = { it.titulo }) { linha ->
                    LinhaCarrosselView(linha = linha, aoClicarJogo = aoClicarJogo)
                }
            }
        }
    }
}

@Composable
private fun LinhaCarrosselView(linha: LinhaCarrossel, aoClicarJogo: (Long) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = linha.titulo,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(linha.jogos, key = { it.jogo.id }) { jogoComPosse ->
                CartaoJogo(
                    jogoComPosse = jogoComPosse,
                    aoClicar = { aoClicarJogo(jogoComPosse.jogo.id) },
                )
            }
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
```

- [ ] **Step 2: Compilar e confirmar que o módulo `:app` builda limpo**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, sem erros.

- [ ] **Step 3: Rodar a suíte de testes completa (garante que nada quebrou)**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test`
Expected: BUILD SUCCESSFUL, todos os testes verdes (os pré-existentes + os 10 novos de `MontadorCarrosseisBibliotecaTest`).

- [ ] **Step 4: Verificação manual (recomendado)**

Rodar o app num emulador/device e abrir a biblioteca: confirmar que aparecem as linhas "Meus jogos" (se houver algum TENHO), "Faltam", os carrosséis de gênero em ordem alfabética e os de ano em ordem cronológica, cada um rolável horizontalmente, e que tocar num jogo ainda navega pro detalhe. Sem teste automatizado de Compose no projeto — essa checagem é visual.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt
git commit -m "feat: TelaBiblioteca — grid único vira carrosséis por categoria"
```
