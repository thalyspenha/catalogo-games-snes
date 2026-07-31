# Filtros e busca na biblioteca Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar busca global por nome na tela de biblioteca (ícone de lupa na TopAppBar), substituindo os carrosséis por um grid de resultado enquanto a busca está ativa.

**Architecture:** Filtro em memória sobre a lista já observada por `BibliotecaViewModel` (`JogoRepository.observarBiblioteca()`), combinada via `Flow.combine` com um `StateFlow<String>` de consulta. Zero mudança em Room/DAO/Repository. O grid de resultado reaproveita um composable extraído de `TelaCategoriaCompleta.kt`.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.coroutines (Flow.combine), JUnit 4.

## Global Constraints

- Busca é só por substring no `nome` do jogo, case-insensitive, sem accent-folding.
- Sem debounce — filtro roda em memória sobre ~1763 itens, custo desprezível por keystroke.
- Sem novo método em `JogoDao`/`JogoRepository` — reaproveita `observarBiblioteca()` que já existe.
- Sem teste automatizado para `BibliotecaViewModel`/Compose (padrão já estabelecido no projeto: só lógica pura ganha JUnit).
- `GridDeJogos` é compartilhado entre `TelaCategoriaCompleta` (comportamento existente, inalterado) e o resultado de busca — não duplicar o grid.
- Consulta em branco: `filtrarPorNome` retorna a lista inteira (contrato da função pura); a decisão de "não filtrar quando vazio" fica no `BibliotecaViewModel` (`resultadoBusca = null`).
- Ambiente Linux NixOS: `export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2` antes de qualquer `./gradlew` (se o caminho não existir mais, `find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d` acha o atual).

---

### Task 1: `filtrarPorNome` — função pura de busca

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt`
- Test: `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt`

**Interfaces:**
- Consumes: `JogoComPosse` (de `com.thalys.catalogosnes.data.local`, já importado no arquivo de teste), campo `jogo.nome: String`.
- Produces: `fun filtrarPorNome(jogos: List<JogoComPosse>, consulta: String): List<JogoComPosse>` — pacote `com.thalys.catalogosnes.ui.biblioteca` (usado pela Task 3).

- [x] **Step 1: Escrever os testes que falham**

Adicionar ao final da classe `MontadorCarrosseisBibliotecaTest` (antes do `}` de fechamento, depois do teste `jogosVisiveis corta nos primeiros 20 quando passa do cap`), reaproveitando o helper `jogo(...)` já definido no topo da classe:

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
```

- [x] **Step 2: Rodar os testes e confirmar que falham (função não existe ainda)**

Run:
```bash
cd /home/thalys/Projetos/Pessoal/catalogo-games-snes
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.MontadorCarrosseisBibliotecaTest"
```
Expected: FAIL — erro de compilação, `filtrarPorNome` não resolvido.

- [x] **Step 3: Implementar `filtrarPorNome`**

Adicionar ao final de `MontadorCarrosseisBiblioteca.kt` (depois da função `jogosVisiveis`):

```kotlin
/**
 * Filtra a biblioteca por substring do nome, case-insensitive, sem accent-folding.
 * Consulta em branco retorna a lista inteira sem filtrar — decisão de "não buscar
 * quando vazio" fica em quem chama.
 */
fun filtrarPorNome(jogos: List<JogoComPosse>, consulta: String): List<JogoComPosse> {
    if (consulta.isBlank()) return jogos
    return jogos.filter { it.jogo.nome.contains(consulta, ignoreCase = true) }
}
```

- [x] **Step 4: Rodar os testes e confirmar que passam**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.MontadorCarrosseisBibliotecaTest"
```
Expected: PASS — todos os testes da classe (os existentes + os 3 novos).

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt
git commit -m "feat: filtrarPorNome — busca por nome na biblioteca"
```

---

### Task 2: `GridDeJogos` — grid compartilhado extraído de `TelaCategoriaCompleta`

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaCategoriaCompleta.kt`

**Interfaces:**
- Consumes: `JogoComPosse`, `CartaoJogoGrid` (já existe no mesmo arquivo, privado), `SeloStatus` (definido em `TelaBiblioteca.kt`, `internal`, mesmo pacote).
- Produces: `@Composable fun GridDeJogos(jogos: List<JogoComPosse>, aoClicarJogo: (Long) -> Unit, mensagemVazia: String, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(8.dp))` — pacote `com.thalys.catalogosnes.ui.biblioteca` (usado pela Task 4).

Refatoração pura — nenhum teste automatizado existe pra Compose neste projeto (ver Global Constraints); verificação é por compilação bem-sucedida + inspeção visual da Task 5.

- [x] **Step 1: Extrair o grid pra `GridDeJogos`, preservando o comportamento atual de `TelaCategoriaCompleta`**

Em `TelaCategoriaCompleta.kt`, trocar o corpo do `when` dentro de `Scaffold` (que hoje tem os três ramos `carregando` / `jogos.isEmpty()` / `else -> LazyVerticalGrid(...)`) por:

```kotlin
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
```

Isso remove o ramo `estado.jogos.isEmpty() -> Box(...) { Text("Nenhum jogo nesta categoria") }` (a mensagem vazia passa a ser responsabilidade de `GridDeJogos`).

Adicionar, no mesmo arquivo, logo antes de `CartaoJogoGrid`:

```kotlin
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
```

**Nota pós-revisão (2026-07-31):** o bloco acima corrige um bug achado na revisão da Task 2 — a versão original deste plano tinha `modifier.fillMaxSize()` sem `.padding(contentPadding)` no ramo vazio, perdendo o inset do `paddingInterno` do Scaffold (o texto "Nenhum jogo nesta categoria" podia renderizar sob a TopAppBar). O `contentPadding` agora é aplicado nos dois ramos.

Adicionar o import que falta no topo do arquivo (`PaddingValues` ainda não é usado em `TelaCategoriaCompleta.kt`):

```kotlin
import androidx.compose.foundation.layout.PaddingValues
```

- [x] **Step 2: Compilar e confirmar que não quebrou nada**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaCategoriaCompleta.kt
git commit -m "refactor: extrai GridDeJogos compartilhado de TelaCategoriaCompleta"
```

---

### Task 3: `BibliotecaViewModel` — estado de busca

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/BibliotecaViewModel.kt`

**Interfaces:**
- Consumes: `filtrarPorNome(jogos, consulta)` (Task 1), `montarCarrosseis(jogos)` (já existe), `JogoRepository.observarBiblioteca(): Flow<List<JogoComPosse>>` (já existe, inalterado).
- Produces: `data class BibliotecaUiState(val linhas: List<LinhaCarrossel>, val resultadoBusca: List<JogoComPosse>?, val consultaBusca: String, val carregando: Boolean)` e `BibliotecaViewModel.aoMudarConsultaBusca(texto: String)` — usados pela Task 4.

Sem teste automatizado (ver Global Constraints); verificação é por compilação + Task 5.

- [ ] **Step 1: Atualizar `BibliotecaUiState` e o `ViewModel` pra combinar lista + consulta**

Substituir o conteúdo de `BibliotecaViewModel.kt` inteiro por:

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Estado exibido pela [com.thalys.catalogosnes.ui.biblioteca.TelaBiblioteca]. */
data class BibliotecaUiState(
    val linhas: List<LinhaCarrossel> = emptyList(),
    val resultadoBusca: List<JogoComPosse>? = null,
    val consultaBusca: String = "",
    val carregando: Boolean = true,
)

/**
 * Observa a biblioteca completa (jogo + posse do usuário) via [JogoRepository], agrupa em
 * carrosséis por categoria via [montarCarrosseis] e expõe como [StateFlow] para a UI.
 * Combina com a consulta de busca ([aoMudarConsultaBusca]): consulta em branco mantém
 * [BibliotecaUiState.resultadoBusca] como `null` (mostra carrosséis); não-vazia filtra a
 * lista via [filtrarPorNome] (mostra grid de resultado).
 */
class BibliotecaViewModel(
    repository: JogoRepository,
) : ViewModel() {

    private val consultaBusca = MutableStateFlow("")

    val estadoUi: StateFlow<BibliotecaUiState> = combine(
        repository.observarBiblioteca(),
        consultaBusca,
    ) { jogos, consulta ->
        BibliotecaUiState(
            linhas = montarCarrosseis(jogos),
            resultadoBusca = if (consulta.isBlank()) null else filtrarPorNome(jogos, consulta),
            consultaBusca = consulta,
            carregando = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BibliotecaUiState(),
    )

    fun aoMudarConsultaBusca(texto: String) {
        consultaBusca.value = texto
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

- [ ] **Step 2: Compilar**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/BibliotecaViewModel.kt
git commit -m "feat: BibliotecaViewModel expõe estado de busca (consultaBusca/resultadoBusca)"
```

---

### Task 4: UI de busca em `TelaBiblioteca`

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt`

**Interfaces:**
- Consumes: `GridDeJogos(...)` (Task 2), `BibliotecaUiState.resultadoBusca`/`consultaBusca` e `BibliotecaViewModel.aoMudarConsultaBusca(texto: String)` (Task 3).
- Produces: nenhuma interface nova exposta a outros arquivos — tarefa terminal da feature.

Sem teste automatizado (ver Global Constraints); verificação visual acontece na Task 5.

- [ ] **Step 1: Adicionar ícone de busca, campo expansível e grid de resultado**

Em `TelaBiblioteca.kt`, adicionar aos imports existentes:

```kotlin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
```

Dentro de `TelaBiblioteca`, logo após a declaração de `chipExpandido` (linha `var chipExpandido by remember { mutableStateOf<TipoCategoria?>(null) }`), adicionar:

```kotlin
    var buscaExpandida by remember { mutableStateOf(false) }
```

Substituir o `Scaffold(topBar = { TopAppBar(...) })` inteiro por:

```kotlin
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (buscaExpandida) {
                        TextField(
                            value = estado.consultaBusca,
                            onValueChange = viewModel::aoMudarConsultaBusca,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Buscar jogo") },
                        )
                    } else {
                        Text("Catálogo SNES")
                    }
                },
                actions = {
                    if (buscaExpandida) {
                        IconButton(onClick = {
                            buscaExpandida = false
                            viewModel.aoMudarConsultaBusca("")
                        }) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingInterno),
            )

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
```

(O `val resultadoBusca = estado.resultadoBusca` local é só pra o compilador do Kotlin fazer smart-cast de `List<JogoComPosse>?` pra `List<JogoComPosse>` dentro do `when` — acessar `estado.resultadoBusca` direto no `when` não teria smart-cast por ser propriedade de outra classe.)

- [ ] **Step 2: Compilar**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt
git commit -m "feat: UI de busca na TelaBiblioteca (ícone de lupa + grid de resultado)"
```

---

### Task 5: Verificação manual no S25

**Files:** nenhum (só verificação — device físico já usado nas features anteriores, serial `RQCY70208AF`).

**Interfaces:**
- Consumes: app completo (Tasks 1-4).
- Produces: evidência visual (screenshots) confirmando a feature funcionando no device real; nenhuma interface de código.

- [ ] **Step 1: Build e instalar**

```bash
cd /home/thalys/Projetos/Pessoal/catalogo-games-snes
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:assembleDebug
adb -s RQCY70208AF install -r app/build/outputs/apk/debug/app-debug.apk
adb -s RQCY70208AF shell am force-stop com.thalys.catalogosnes
adb -s RQCY70208AF shell am start -n com.thalys.catalogosnes/.MainActivity
```
Expected: `BUILD SUCCESSFUL`, `Success` no install, app abre sem crash.

- [ ] **Step 2: Abrir a busca e digitar uma consulta que bate em algum jogo já sincronizado no device**

Usar `adb shell uiautomator dump` pra achar o ícone com `content-desc="Buscar jogo"` (bounds → tap no centro). Depois tocar no campo de texto (área do título) pra focar, digitar via `adb shell input text "<termo>"` — usar um termo que bata em algum dos jogos já sincronizados nesse device (ex: um dos 5 sincronizados no teste da sessão anterior, ou qualquer termo genérico se o catálogo tiver mais dados). Tirar screenshot:

```bash
adb -s RQCY70208AF shell screencap -p /sdcard/busca_resultado.png
adb -s RQCY70208AF pull /sdcard/busca_resultado.png /tmp/claude-1000/-home-thalys-Projetos-Pessoal-catalogo-games-snes/1864b5c6-963d-4b3d-ad9e-c42d17b04ac7/scratchpad/busca_resultado_s25.png
```
Expected: grid de resultado aparece no lugar dos carrosséis, mostrando só jogos cujo nome bate com o termo digitado.

- [ ] **Step 3: Digitar uma consulta sem nenhum resultado**

Limpar o campo (seleciona tudo + apaga, ou fecha e reabre a busca) e digitar um termo que não bate em nenhum jogo (ex: "zzzzz"). Screenshot:

```bash
adb -s RQCY70208AF shell screencap -p /sdcard/busca_vazia.png
adb -s RQCY70208AF pull /sdcard/busca_vazia.png /tmp/claude-1000/-home-thalys-Projetos-Pessoal-catalogo-games-snes/1864b5c6-963d-4b3d-ad9e-c42d17b04ac7/scratchpad/busca_vazia_s25.png
```
Expected: texto "Nenhum jogo encontrado" centralizado, sem crash.

- [ ] **Step 4: Fechar a busca (ícone X) e confirmar volta pros carrosséis**

Achar via uiautomator dump o ícone com `content-desc="Fechar busca"`, tocar. Screenshot:

```bash
adb -s RQCY70208AF shell screencap -p /sdcard/busca_fechada.png
adb -s RQCY70208AF pull /sdcard/busca_fechada.png /tmp/claude-1000/-home-thalys-Projetos-Pessoal-catalogo-games-snes/1864b5c6-963d-4b3d-ad9e-c42d17b04ac7/scratchpad/busca_fechada_s25.png
```
Expected: chips + carrosséis normais de volta, campo de busca colapsado, título "Catálogo SNES" visível de novo.

- [ ] **Step 5: Checar logcat por crash em todo o fluxo**

```bash
adb -s RQCY70208AF logcat -d -t 300 '*:E' | grep -iE "AndroidRuntime|FATAL|catalogosnes" || echo "sem erros"
```
Expected: `sem erros` (ou nenhuma linha relevante).

---

## Self-Review

**Cobertura da spec:** escopo (busca global) → Tasks 3-4; campo nome/substring/case-insensitive → Task 1; comportamento "lista única substitui carrosséis" → Task 4; filtro em memória sem debounce → Tasks 1/3; entrada via ícone de lupa expansível → Task 4; reaproveitamento do grid → Task 2; consulta sobrevive a navegar pro detalhe e volta (fica no ViewModel) mas reseta ao sair/voltar da tela (estado local do Compose) → coberto naturalmente pela Task 3 (StateFlow no ViewModel, sobrevive por `WhileSubscribed(5_000)`) + Task 4 (`buscaExpandida` é `remember` local, não sobrevive a recriar o composable). Testes → Task 1 (únicos automatizados, conforme decisão da spec). Verificação no device real → Task 5.

**Placeholders:** nenhum "TBD"/"implementar depois" — todo código é literal, pronto pra colar.

**Consistência de tipos:** `filtrarPorNome(jogos: List<JogoComPosse>, consulta: String): List<JogoComPosse>` (Task 1) usado idêntico em `BibliotecaViewModel` (Task 3). `GridDeJogos(jogos: List<JogoComPosse>, aoClicarJogo: (Long) -> Unit, mensagemVazia: String, modifier: Modifier, contentPadding: PaddingValues)` (Task 2) chamado com os mesmos nomes de parâmetro em `TelaCategoriaCompleta` (Task 2) e `TelaBiblioteca` (Task 4). `BibliotecaUiState.resultadoBusca: List<JogoComPosse>?` e `.consultaBusca: String` (Task 3) usados com esses nomes exatos em `TelaBiblioteca` (Task 4). `aoMudarConsultaBusca(texto: String)` (Task 3) chamado como `viewModel::aoMudarConsultaBusca` (Task 4) — assinatura bate (`(String) -> Unit`).
