# Grid único na home + menu lateral de filtro — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir os carrosséis por categoria da `TelaBiblioteca` por um grid único
de 4 colunas com o catálogo completo (sempre visível ao abrir o app), filtrado sob
demanda por um menu lateral (drawer), com cards de altura padronizada.

**Architecture:** Nova função pura `filtrarBiblioteca()` (um filtro por vez:
Todos/Tenho/Quero ter/Faltam/Gênero/Ano) substitui `montarCarrosseis()`.
`BibliotecaViewModel` expõe a lista já filtrada + estado do filtro ativo.
`TelaBiblioteca` vira um `ModalNavigationDrawer` com `GridDeJogos` (4 colunas) no
corpo. Código de carrosséis/"Ver tudo" (`MontadorCarrosseisBiblioteca`,
`TelaCategoriaCompleta`, rota `categoria/{titulo}`) é removido por ficar sem uso.

**Tech Stack:** Kotlin + Jetpack Compose (Material3 `ModalNavigationDrawer`/
`NavigationDrawerItem`, já disponíveis na Compose BOM 2024.10.01 do projeto), JUnit
4.13.2 pra lógica pura.

## Global Constraints

- Um filtro ativo por vez (radio-like) — não combina status + gênero + ano.
- Selecionar um filtro no drawer fecha o drawer e aplica na hora.
- Busca por nome ignora o filtro ativo — sempre pesquisa nos 1763 jogos (comportamento já existente, não muda).
- Tela inicial sempre abre com filtro `Todos` (catálogo completo) — filtro não é persistido entre aberturas do app.
- Gênero/Ano no submenu do drawer incluem "Sem gênero"/"Sem ano" quando houver jogo sem esse dado.
- `Faltam` = `status != TENHO && status != NAO_INTERESSA` (inclui `QUERO_TER` e posse nula — duplicação com `QueroTer` é proposital).
- Grid: `GridCells.Fixed(4)`, espaçamento entre cards via `Arrangement.spacedBy(4.dp)`.
- Nome do jogo no card usa `minLines = 2` + `maxLines = 2` (altura de card sempre igual, corta com reticências).
- Código de carrosséis/"Ver tudo" sem uso após a migração é removido, não deixado dormente.

---

### Task 1: `FiltroBiblioteca` — filtro por status/gênero/ano + listas de valores disponíveis

**Files:**
- Create: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBiblioteca.kt`
- Test: `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBibliotecaTest.kt`

**Interfaces:**
- Consumes: `com.thalys.catalogosnes.data.local.JogoComPosse`, `com.thalys.catalogosnes.data.local.JogoEntity`, `com.thalys.catalogosnes.data.local.PosseUsuarioEntity`, `com.thalys.catalogosnes.data.model.StatusPosse` (todos já existentes, sem mudança).
- Produces (usado pela Task 2): `sealed class FiltroBiblioteca` com `object Todos`, `object Tenho`, `object QueroTer`, `object Faltam`, `data class Genero(val valor: String)`, `data class Ano(val valor: String)`; `fun filtrarBiblioteca(jogos: List<JogoComPosse>, filtro: FiltroBiblioteca): List<JogoComPosse>`; `fun generosDisponiveis(jogos: List<JogoComPosse>): List<String>`; `fun anosDisponiveis(jogos: List<JogoComPosse>): List<String>`.

Este arquivo é só aditivo (não toca em nenhum arquivo existente) — zero risco de quebrar o build atual, que continua usando `montarCarrosseis()` normalmente até a Task 2.

- [ ] **Step 1: Escrever os testes (todos falhando, arquivo/funções ainda não existem)**

```kotlin
package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.local.JogoEntity
import com.thalys.catalogosnes.data.local.PosseUsuarioEntity
import com.thalys.catalogosnes.data.model.StatusPosse
import org.junit.Assert.assertEquals
import org.junit.Test

class FiltroBibliotecaTest {

    private fun jogo(
        id: Long,
        nome: String = "J$id",
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
    fun `filtro Todos retorna a lista inteira`() {
        val jogos = listOf(JogoComPosse(jogo(1), null), JogoComPosse(jogo(2), null))
        assertEquals(jogos, filtrarBiblioteca(jogos, FiltroBiblioteca.Todos))
    }

    @Test
    fun `filtro Tenho retorna so status TENHO`() {
        val jogos = listOf(
            JogoComPosse(jogo(1), posse(1, StatusPosse.TENHO)),
            JogoComPosse(jogo(2), posse(2, StatusPosse.QUERO_TER)),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Tenho)
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro QueroTer retorna so status QUERO_TER`() {
        val jogos = listOf(
            JogoComPosse(jogo(1), posse(1, StatusPosse.QUERO_TER)),
            JogoComPosse(jogo(2), posse(2, StatusPosse.TENHO)),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.QueroTer)
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Faltam inclui quero ter e sem posse, exclui tenho e nao interessa`() {
        val jogos = listOf(
            JogoComPosse(jogo(1), posse(1, StatusPosse.QUERO_TER)),
            JogoComPosse(jogo(2), posse(2, StatusPosse.TENHO)),
            JogoComPosse(jogo(3), posse(3, StatusPosse.NAO_INTERESSA)),
            JogoComPosse(jogo(4), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Faltam)
        assertEquals(listOf(1L, 4L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Genero com valor real casa pelo nome exato`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, genero = "RPG"), null),
            JogoComPosse(jogo(2, genero = "Ação"), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Genero("RPG"))
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Genero Sem genero casa jogos sem genero ou com genero em branco`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, genero = "RPG"), null),
            JogoComPosse(jogo(2, genero = null), null),
            JogoComPosse(jogo(3, genero = "   "), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Genero("Sem gênero"))
        assertEquals(listOf(2L, 3L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Ano com valor real casa pelo ano exato`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, ano = 1994), null),
            JogoComPosse(jogo(2, ano = 1996), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Ano("1994"))
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtro Ano Sem ano casa jogos sem ano cadastrado`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, ano = 1994), null),
            JogoComPosse(jogo(2, ano = null), null),
        )
        val resultado = filtrarBiblioteca(jogos, FiltroBiblioteca.Ano("Sem ano"))
        assertEquals(listOf(2L), resultado.map { it.jogo.id })
    }

    @Test
    fun `generosDisponiveis ordena alfabetico e poe Sem genero no fim`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, genero = "RPG"), null),
            JogoComPosse(jogo(2, genero = "Ação"), null),
            JogoComPosse(jogo(3, genero = null), null),
        )
        assertEquals(listOf("Ação", "RPG", "Sem gênero"), generosDisponiveis(jogos))
    }

    @Test
    fun `generosDisponiveis sem nenhum jogo sem genero, nao inclui Sem genero`() {
        val jogos = listOf(JogoComPosse(jogo(1, genero = "RPG"), null))
        assertEquals(listOf("RPG"), generosDisponiveis(jogos))
    }

    @Test
    fun `anosDisponiveis ordena cronologico e poe Sem ano no fim`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, ano = 1996), null),
            JogoComPosse(jogo(2, ano = 1992), null),
            JogoComPosse(jogo(3, ano = null), null),
        )
        assertEquals(listOf("1992", "1996", "Sem ano"), anosDisponiveis(jogos))
    }

    @Test
    fun `anosDisponiveis sem nenhum jogo sem ano, nao inclui Sem ano`() {
        val jogos = listOf(JogoComPosse(jogo(1, ano = 1994), null))
        assertEquals(listOf("1994"), anosDisponiveis(jogos))
    }
}
```

- [ ] **Step 2: Rodar os testes e confirmar que falham (arquivo principal não existe)**

Run: `export JAVA_HOME=$(find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d | head -1) && ./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.FiltroBibliotecaTest"`
Expected: FAIL (compilation error — `FiltroBiblioteca`/`filtrarBiblioteca`/`generosDisponiveis`/`anosDisponiveis` não existem ainda).

- [ ] **Step 3: Criar `FiltroBiblioteca.kt` com a implementação mínima**

```kotlin
package com.thalys.catalogosnes.ui.biblioteca

import com.thalys.catalogosnes.data.local.JogoComPosse
import com.thalys.catalogosnes.data.model.StatusPosse

private const val SEM_GENERO = "Sem gênero"
private const val SEM_ANO = "Sem ano"

/** Filtro ativo na biblioteca: só um por vez (radio-like), aplicado por [filtrarBiblioteca]. */
sealed class FiltroBiblioteca {
    object Todos : FiltroBiblioteca()
    object Tenho : FiltroBiblioteca()
    object QueroTer : FiltroBiblioteca()
    object Faltam : FiltroBiblioteca()
    data class Genero(val valor: String) : FiltroBiblioteca()
    data class Ano(val valor: String) : FiltroBiblioteca()
}

/** Aplica [filtro] sobre [jogos]. `Faltam` inclui `QUERO_TER` e posse nula, propositalmente
 * (jogo ainda falta na coleção mesmo estando na lista de desejos). */
fun filtrarBiblioteca(jogos: List<JogoComPosse>, filtro: FiltroBiblioteca): List<JogoComPosse> =
    when (filtro) {
        FiltroBiblioteca.Todos -> jogos
        FiltroBiblioteca.Tenho -> jogos.filter { it.posse?.status == StatusPosse.TENHO }
        FiltroBiblioteca.QueroTer -> jogos.filter { it.posse?.status == StatusPosse.QUERO_TER }
        FiltroBiblioteca.Faltam -> jogos.filter {
            it.posse?.status != StatusPosse.TENHO && it.posse?.status != StatusPosse.NAO_INTERESSA
        }
        is FiltroBiblioteca.Genero -> jogos.filter { jogoComPosse ->
            val genero = jogoComPosse.jogo.genero?.takeIf { it.isNotBlank() }
            if (filtro.valor == SEM_GENERO) genero == null else genero == filtro.valor
        }
        is FiltroBiblioteca.Ano -> jogos.filter { jogoComPosse ->
            val ano = jogoComPosse.jogo.anoLancamento?.toString()
            if (filtro.valor == SEM_ANO) ano == null else ano == filtro.valor
        }
    }

/** Gêneros distintos presentes em [jogos], A-Z, com "Sem gênero" no fim se houver algum jogo sem gênero (ou em branco). */
fun generosDisponiveis(jogos: List<JogoComPosse>): List<String> {
    val porGenero = jogos.groupBy { it.jogo.genero?.takeIf { genero -> genero.isNotBlank() } }
    val generos = porGenero.keys.filterNotNull().sorted()
    return if (porGenero.containsKey(null)) generos + SEM_GENERO else generos
}

/** Anos distintos presentes em [jogos], cronológico, com "Sem ano" no fim se houver algum jogo sem ano. */
fun anosDisponiveis(jogos: List<JogoComPosse>): List<String> {
    val porAno = jogos.groupBy { it.jogo.anoLancamento }
    val anos = porAno.keys.filterNotNull().sorted().map { it.toString() }
    return if (porAno.containsKey(null)) anos + SEM_ANO else anos
}
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `export JAVA_HOME=$(find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d | head -1) && ./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.FiltroBibliotecaTest"`
Expected: PASS, 13 testes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBiblioteca.kt app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBibliotecaTest.kt
git commit -m "feat: FiltroBiblioteca — filtro por status/genero/ano, um por vez"
```

---

### Task 2: Grid único + menu lateral na `TelaBiblioteca`, remoção dos carrosséis

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/BibliotecaViewModel.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/navigation/CatalogoNavHost.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBiblioteca.kt` (recebe `filtrarPorNome`, movida de `MontadorCarrosseisBiblioteca.kt`)
- Modify: `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBibliotecaTest.kt` (recebe os 4 testes de `filtrarPorNome`)
- Delete: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt`
- Delete: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaCategoriaCompleta.kt`
- Delete: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/CategoriaCompletaViewModel.kt`
- Delete: `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt`

**Interfaces:**
- Consumes: `FiltroBiblioteca`/`filtrarBiblioteca`/`generosDisponiveis`/`anosDisponiveis` (Task 1), `JogoRepository.observarBiblioteca(): Flow<List<JogoComPosse>>` (já existente, sem mudança), `JogoEntity.modeloCapa()` (já existente).
- Produces: `data class BibliotecaUiState(jogosFiltrados: List<JogoComPosse>, filtroSelecionado: FiltroBiblioteca, generosDisponiveis: List<String>, anosDisponiveis: List<String>, resultadoBusca: List<JogoComPosse>?, carregando: Boolean)` com `val tituloTopBar: String`; `BibliotecaViewModel.aoSelecionarFiltro(filtro: FiltroBiblioteca)`; `fun GridDeJogos(...)` (mesma assinatura pública de hoje, só muda de arquivo — de `TelaCategoriaCompleta.kt` pra `TelaBiblioteca.kt` — e de 3 pra 4 colunas).

Este é um único task porque `BibliotecaViewModel`, `TelaBiblioteca`, `CatalogoNavHost`,
`TelaCategoriaCompleta` e `CategoriaCompletaViewModel` só compilam juntos — mudar um
sem os outros quebra o build (confirmado por grep antes de escrever este plano: nenhum
desses símbolos é referenciado fora deste conjunto de arquivos).

- [ ] **Step 1: Reescrever `BibliotecaViewModel.kt`**

```kotlin
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
 */
class BibliotecaViewModel(
    repository: JogoRepository,
) : ViewModel() {

    private val _consultaBusca = MutableStateFlow("")
    val consultaBusca: StateFlow<String> = _consultaBusca.asStateFlow()

    private val _filtroSelecionado = MutableStateFlow<FiltroBiblioteca>(FiltroBiblioteca.Todos)

    val estadoUi: StateFlow<BibliotecaUiState> = combine(
        repository.observarBiblioteca(),
        _filtroSelecionado,
        _consultaBusca,
    ) { jogos, filtro, consulta ->
        BibliotecaUiState(
            jogosFiltrados = filtrarBiblioteca(jogos, filtro),
            filtroSelecionado = filtro,
            generosDisponiveis = generosDisponiveis(jogos),
            anosDisponiveis = anosDisponiveis(jogos),
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
```

- [ ] **Step 2: Mover `filtrarPorNome` pra `FiltroBiblioteca.kt`**

Adicionar ao final de `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBiblioteca.kt`:

```kotlin

/**
 * Filtra a biblioteca por substring do nome, case-insensitive, sem accent-folding.
 * Consulta em branco retorna a lista inteira sem filtrar — decisão de "não buscar
 * quando vazio" fica em quem chama.
 */
fun filtrarPorNome(jogos: List<JogoComPosse>, consulta: String): List<JogoComPosse> {
    val termo = consulta.trim()
    return jogos.filter { it.jogo.nome.contains(termo, ignoreCase = true) }
}
```

E ao final de `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBibliotecaTest.kt`, dentro da classe (antes do `}` final):

```kotlin

    @Test
    fun `filtrarPorNome acha substring no meio do nome, case-insensitive`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "The Legend of Zelda: A Link to the Past"), null),
            JogoComPosse(jogo(2, "Chrono Trigger"), null),
        )
        val resultado = filtrarPorNome(jogos, "zelda")
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }

    @Test
    fun `filtrarPorNome sem nenhum resultado retorna lista vazia`() {
        val jogos = listOf(JogoComPosse(jogo(1, "Chrono Trigger"), null))
        val resultado = filtrarPorNome(jogos, "mario")
        assertEquals(emptyList<JogoComPosse>(), resultado)
    }

    @Test
    fun `filtrarPorNome com consulta em branco retorna a lista inteira`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), null),
            JogoComPosse(jogo(2, "B"), null),
        )
        assertEquals(jogos, filtrarPorNome(jogos, ""))
        assertEquals(jogos, filtrarPorNome(jogos, "   "))
    }

    @Test
    fun `filtrarPorNome ignora espaco no inicio e fim da consulta`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "The Legend of Zelda: A Link to the Past"), null),
            JogoComPosse(jogo(2, "Chrono Trigger"), null),
        )
        val resultado = filtrarPorNome(jogos, "zelda ")
        assertEquals(listOf(1L), resultado.map { it.jogo.id })
    }
```

- [ ] **Step 3: Reescrever `TelaBiblioteca.kt`**

```kotlin
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
```

- [ ] **Step 4: Atualizar `CatalogoNavHost.kt`**

```kotlin
package com.thalys.catalogosnes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca
import com.thalys.catalogosnes.ui.detalhe.TelaDetalheJogo
import com.thalys.catalogosnes.ui.sincronizacao.TelaSincronizacao

private const val ROTA_BIBLIOTECA = "biblioteca"
private const val ARGUMENTO_JOGO_ID = "jogoId"
private const val ROTA_DETALHE = "detalhe/{$ARGUMENTO_JOGO_ID}"
private const val ROTA_SINCRONIZACAO = "sincronizacao"

/**
 * Grafo de navegação do app: biblioteca (grid único, filtro via menu lateral) -> detalhe/edição
 * de posse de um jogo, ou -> tela de sincronização.
 */
@Composable
fun CatalogoNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROTA_BIBLIOTECA) {
        composable(ROTA_BIBLIOTECA) {
            TelaBiblioteca(
                aoClicarJogo = { jogoId -> navController.navigate("detalhe/$jogoId") },
                aoClicarSincronizar = { navController.navigate(ROTA_SINCRONIZACAO) },
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
    }
}
```

- [ ] **Step 5: Apagar os arquivos de carrossel sem uso**

```bash
git rm app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt
git rm app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaCategoriaCompleta.kt
git rm app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/CategoriaCompletaViewModel.kt
git rm app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt
```

- [ ] **Step 6: Rodar todos os testes JUnit do módulo `:app`**

Run: `export JAVA_HOME=$(find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d | head -1) && ./gradlew :app:testDebugUnitTest`
Expected: PASS, sem nenhum teste de `MontadorCarrosseisBibliotecaTest` (deletado) e com `FiltroBibliotecaTest` mostrando 17 testes (13 da Task 1 + 4 de `filtrarPorNome` movidos).

- [ ] **Step 7: Build completo do app**

Run: `export JAVA_HOME=$(find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d | head -1) && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, sem erro de referência a símbolo removido (`LinhaCarrossel`, `TipoCategoria`, `montarCarrosseis`, `TelaCategoriaCompleta`, `BarraDeIndice`, `CartaoVerTudo`, `mostrarVerTudo`, `jogosVisiveis`, `aoClicarVerTudo`).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/BibliotecaViewModel.kt app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt app/src/main/java/com/thalys/catalogosnes/ui/navigation/CatalogoNavHost.kt app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBiblioteca.kt app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/FiltroBibliotecaTest.kt
git commit -m "feat: grid unico de 4 colunas + menu lateral de filtro na biblioteca

Remove carrosseis por categoria, barra de indice fixa e tela 'Ver
tudo'. Home sempre abre com o catalogo completo (filtro Todos); menu
lateral aplica um filtro por vez (status/genero/ano). Cards com altura
padronizada (minLines=2 no nome)."
```

---

### Task 3: Verificação manual no S25 físico

**Files:** nenhum (só verificação, sem mudança de código).

**Interfaces:** N/A — task de verificação.

- [ ] **Step 1: Descobrir o serial do device conectado**

Run: `adb devices`
Expected: lista com um device físico autorizado (serial pode não ser mais `RQCY70208AF` — usar o que aparecer).

- [ ] **Step 2: Build + instalar**

Run: `export JAVA_HOME=$(find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d | head -1) && ./gradlew :app:assembleDebug && adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: instala sem erro (banco/capas locais existentes preservados — `install -r`, não `-r -d` nem uninstall).

- [ ] **Step 3: Abrir o app e conferir a home**

Run: `adb -s <serial> shell am start -n com.thalys.catalogosnes/.MainActivity` e observar a tela.
Expected: abre com um grid de 4 colunas mostrando o catálogo completo (~1763 jogos, sem filtro), título "Catálogo SNES" na TopAppBar, cards todos com a mesma altura (nome longo não estica o card).

- [ ] **Step 4: Testar o menu lateral**

Tocar no ícone de hambúrguer (topo esquerdo) → abre o drawer com Todos/Tenho/Quero ter/Faltam/Gênero/Ano. Tocar em "Tenho".
Expected: drawer fecha, título muda pra "Tenho", grid mostra só os jogos com status Tenho.

- [ ] **Step 5: Testar submenu de Gênero**

Abrir o drawer de novo, tocar em "Gênero" (expande lista), escolher um valor.
Expected: drawer fecha, título muda pro nome do gênero escolhido, grid filtra só aquele gênero.

- [ ] **Step 6: Testar botão voltar do sistema com o drawer aberto**

Abrir o drawer, apertar o botão de voltar do Android (não tocar em nada dentro do drawer).
Expected: drawer fecha, app continua na tela da biblioteca (não fecha o app).

- [ ] **Step 7: Testar busca ignorando o filtro ativo**

Com um filtro diferente de Todos ainda selecionado (ex: um gênero específico sem o jogo "Zelda"), abrir a busca (lupa) e digitar "Zelda".
Expected: mostra resultado de "Zelda" mesmo que esse jogo não pertença ao gênero filtrado — confirma que a busca ignora o filtro ativo.

- [ ] **Step 8: Conferir logcat**

Run: `adb -s <serial> logcat -d | grep -i "AndroidRuntime\|FATAL"` (rodar logo depois dos passos acima)
Expected: nenhuma linha de crash/exception relacionada ao app.

- [ ] **Step 9: Registrar o resultado**

Sem commit de código nesta task — só atualizar `CLAUDE.md` (seção "Status atual") e a memória do projeto com o resultado da verificação, seguindo o padrão já usado nas features anteriores deste projeto.
