# Filtro "Quero ter" na biblioteca Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar uma linha de carrossel dedicada "Quero ter" (jogos com `StatusPosse.QUERO_TER`) na `TelaBiblioteca`, com chip próprio na barra de índice, sem alterar o comportamento existente do carrossel "Faltam".

**Architecture:** Segue o padrão já existente para "Meus jogos": um novo valor no enum `TipoCategoria`, um novo filtro em `montarCarrosseis()` (função pura, testada via JUnit) e um novo `AssistChip` em `BarraDeIndice` (Compose, sem teste automatizado — verificação manual no S25 físico, mesmo padrão já usado nas features anteriores deste projeto).

**Tech Stack:** Kotlin, JUnit 4 (testes de `montarCarrosseis`), Jetpack Compose (Material3 `AssistChip`).

## Global Constraints

- Comunicação e nomes de identificadores em português do Brasil (pt-br), seguindo o padrão já usado no arquivo (`TITULO_MEUS_JOGOS`, `rolarParaPrimeiraDoTipo`, etc.).
- Não alterar o filtro/comportamento de "Faltam" — um jogo QUERO_TER deve continuar aparecendo em "Faltam" também (duplicação intencional, decisão já tomada na spec).
- `TipoCategoria` é enum aditivo: adicionar `QUERO_TER` não pode quebrar nenhum `when` exaustivo existente (confirmado por grep na spec que não há nenhum).
- Nenhuma mudança em `TelaCategoriaCompleta.kt` — já reaproveita `LinhaCarrossel` genericamente por `tipo`.

---

### Task 1: Linha "Quero ter" em `montarCarrosseis()`

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt`
- Test: `app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt`

**Interfaces:**
- Consumes: `JogoComPosse` (campo `posse: PosseUsuarioEntity?`, `posse.status: StatusPosse`), `StatusPosse.QUERO_TER` (já existe em `data/model/StatusPosse.kt`).
- Produces: `TipoCategoria.QUERO_TER` (novo valor de enum) e a linha `"Quero ter"` dentro da lista retornada por `montarCarrosseis()`, na posição entre "Meus jogos" e "Faltam". Task 2 depende desses dois nomes exatos (`TipoCategoria.QUERO_TER`, título `"Quero ter"`).

- [ ] **Step 1: Escrever os testes que falham**

Adicionar ao final da classe `MontadorCarrosseisBibliotecaTest` (antes da chave de fechamento final), reaproveitando os helpers `jogo(...)` e `posse(...)` já existentes no arquivo:

```kotlin
    @Test
    fun `jogo com status quero ter aparece na linha quero ter`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.QUERO_TER)),
            JogoComPosse(jogo(2, "B"), posse(2, StatusPosse.TENHO)),
        )

        val linhas = montarCarrosseis(jogos)
        val queroTer = linhas.first { it.titulo == "Quero ter" }

        assertEquals(listOf(1L), queroTer.jogos.map { it.jogo.id })
    }

    @Test
    fun `jogo com status quero ter tambem aparece em faltam`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.QUERO_TER)),
        )

        val linhas = montarCarrosseis(jogos)
        val faltam = linhas.first { it.titulo == "Faltam" }

        assertEquals(listOf(1L), faltam.jogos.map { it.jogo.id })
    }

    @Test
    fun `sem nenhum jogo quero ter, linha quero ter nao aparece`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.TENHO)),
        )

        val linhas = montarCarrosseis(jogos)

        assertEquals(null, linhas.firstOrNull { it.titulo == "Quero ter" })
    }

    @Test
    fun `ordem das categorias inclui quero ter entre meus jogos e faltam`() {
        val jogos = listOf(
            JogoComPosse(jogo(1, "A", genero = "RPG", ano = 1994), posse(1, StatusPosse.TENHO)),
            JogoComPosse(jogo(2, "B", genero = "Ação", ano = 1995), posse(2, StatusPosse.QUERO_TER)),
        )

        val linhas = montarCarrosseis(jogos)

        assertEquals(
            listOf("Meus jogos", "Quero ter", "Faltam", "Ação", "RPG", "1994", "1995"),
            linhas.map { it.titulo },
        )
    }

    @Test
    fun `linha de quero ter tem tipo QUERO_TER`() {
        val jogos = listOf(JogoComPosse(jogo(1, "A"), posse(1, StatusPosse.QUERO_TER)))
        val linhas = montarCarrosseis(jogos)
        assertEquals(TipoCategoria.QUERO_TER, linhas.first { it.titulo == "Quero ter" }.tipo)
    }
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `JAVA_HOME=$(find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d | head -1) ./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.MontadorCarrosseisBibliotecaTest"`

Expected: FAIL — `Unresolved reference: QUERO_TER` (o valor ainda não existe em `TipoCategoria`) e/ou os testes de conteúdo/ordem falham porque a linha "Quero ter" ainda não é gerada.

- [ ] **Step 3: Implementar `TipoCategoria.QUERO_TER` e o filtro em `montarCarrosseis()`**

Em `MontadorCarrosseisBiblioteca.kt`:

```kotlin
enum class TipoCategoria { MEUS_JOGOS, QUERO_TER, FALTAM, GENERO, ANO }
```

Adicionar a constante de título junto das outras (`TITULO_MEUS_JOGOS`, `TITULO_FALTAM`, etc.):

```kotlin
private const val TITULO_QUERO_TER = "Quero ter"
```

Dentro de `montarCarrosseis()`, inserir o bloco de "Quero ter" **entre** o bloco de "Meus jogos" e o bloco de "Faltam" (o bloco de "Faltam" continua exatamente como está, sem nenhuma alteração):

```kotlin
    val meusJogos = jogos.filter { it.posse?.status == StatusPosse.TENHO }
    if (meusJogos.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_MEUS_JOGOS, meusJogos, TipoCategoria.MEUS_JOGOS)
    }

    val queroTer = jogos.filter { it.posse?.status == StatusPosse.QUERO_TER }
    if (queroTer.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_QUERO_TER, queroTer, TipoCategoria.QUERO_TER)
    }

    val faltam = jogos.filter {
        it.posse?.status != StatusPosse.TENHO && it.posse?.status != StatusPosse.NAO_INTERESSA
    }
    if (faltam.isNotEmpty()) {
        linhas += LinhaCarrossel(TITULO_FALTAM, faltam, TipoCategoria.FALTAM)
    }
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `JAVA_HOME=$(find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d | head -1) ./gradlew :app:testDebugUnitTest --tests "com.thalys.catalogosnes.ui.biblioteca.MontadorCarrosseisBibliotecaTest"`

Expected: PASS (todos os testes da classe, os novos e os já existentes).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBiblioteca.kt app/src/test/java/com/thalys/catalogosnes/ui/biblioteca/MontadorCarrosseisBibliotecaTest.kt
git commit -m "feat: linha de carrossel Quero ter em montarCarrosseis"
```

---

### Task 2: Chip "Quero ter" na barra de índice

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt`

**Interfaces:**
- Consumes: `TipoCategoria.QUERO_TER` e a linha de título `"Quero ter"` (produzidos pela Task 1), `rolarParaPrimeiraDoTipo(tipo: TipoCategoria)` (já existe em `TelaBiblioteca.kt:112`), `BarraDeIndice` (composable privado em `TelaBiblioteca.kt:217`).
- Produces: nada consumido por outra task — esta é a última task do plano.

- [ ] **Step 1: Adicionar o parâmetro e o chip em `BarraDeIndice`**

Em `TelaBiblioteca.kt`, na assinatura de `BarraDeIndice` (por volta da linha 217-224), adicionar o novo callback ao lado de `aoTocarFaltam`:

```kotlin
private fun BarraDeIndice(
    linhas: List<LinhaCarrossel>,
    chipExpandido: TipoCategoria?,
    aoTocarMeusJogos: () -> Unit,
    aoTocarQueroTer: () -> Unit,
    aoTocarFaltam: () -> Unit,
    aoAlternarGenero: () -> Unit,
    aoAlternarAno: () -> Unit,
    aoEscolherValor: (String) -> Unit,
) {
    val temMeusJogos = linhas.any { it.tipo == TipoCategoria.MEUS_JOGOS }
    val temQueroTer = linhas.any { it.tipo == TipoCategoria.QUERO_TER }
    val temFaltam = linhas.any { it.tipo == TipoCategoria.FALTAM }
```

E na `Row` de chips, inserir o novo `AssistChip` entre "Meus jogos" e "Faltam":

```kotlin
            AssistChip(onClick = aoTocarMeusJogos, enabled = temMeusJogos, label = { Text("Meus jogos") }, modifier = Modifier.padding(end = 8.dp))
            AssistChip(onClick = aoTocarQueroTer, enabled = temQueroTer, label = { Text("Quero ter") }, modifier = Modifier.padding(end = 8.dp))
            AssistChip(onClick = aoTocarFaltam, enabled = temFaltam, label = { Text("Faltam") }, modifier = Modifier.padding(end = 8.dp))
```

- [ ] **Step 2: Passar o novo callback no call site de `BarraDeIndice`**

Em `TelaBiblioteca.kt`, no corpo de `TelaBiblioteca` (por volta da linha 182-197), adicionar `aoTocarQueroTer` à chamada de `BarraDeIndice`:

```kotlin
                BarraDeIndice(
                    linhas = estado.linhas,
                    chipExpandido = chipExpandido,
                    aoTocarMeusJogos = { rolarParaPrimeiraDoTipo(TipoCategoria.MEUS_JOGOS) },
                    aoTocarQueroTer = { rolarParaPrimeiraDoTipo(TipoCategoria.QUERO_TER) },
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
```

- [ ] **Step 3: Build**

Run: `JAVA_HOME=$(find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d | head -1) ./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Rodar a suíte completa de testes (garantir que nada quebrou)**

Run: `JAVA_HOME=$(find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d | head -1) ./gradlew :app:testDebugUnitTest`

Expected: BUILD SUCCESSFUL, todos os testes passam (os 5 novos da Task 1 + todos os já existentes).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt
git commit -m "feat: chip Quero ter na barra de indice da biblioteca"
```

- [ ] **Step 6: Verificação manual no S25 físico**

Instalar o build de debug e confirmar visualmente (mesmo processo já usado nas features anteriores — device serial `RQCY70208AF`):

```bash
adb -s RQCY70208AF install -r app/build/outputs/apk/debug/app-debug.apk
adb -s RQCY70208AF shell am start -n com.thalys.catalogosnes/.MainActivity
```

Checklist:
1. Marcar pelo menos um jogo como "Quero ter" na tela de detalhe (se ainda não houver nenhum).
2. Voltar pra biblioteca: confirmar que existe uma linha "Quero ter" entre "Meus jogos" e "Faltam", com o(s) jogo(s) certo(s).
3. Confirmar que o mesmo jogo marcado "Quero ter" também aparece na linha "Faltam" (duplicação intencional).
4. Na barra de chips do topo, tocar no chip "Quero ter": a tela deve rolar até a linha "Quero ter".
5. Sem nenhum jogo "Quero ter" (ex: banco recém-populado sem posse marcada), confirmar que o chip "Quero ter" aparece desabilitado e nenhuma linha "Quero ter" é exibida.
6. Checar `adb -s RQCY70208AF logcat` durante o teste: sem crash/ANR.

---

## Self-Review

**Cobertura da spec:** enum `QUERO_TER` (Task 1, Step 3) ✓; filtro em `montarCarrosseis` inserido entre Meus jogos e Faltam, sem alterar o bloco de Faltam (Task 1, Step 3) ✓; chip na `BarraDeIndice` na mesma posição (Task 2, Step 1-2) ✓; testes cobrindo conteúdo/ordem/tipo/linha-vazia (Task 1, Step 1) ✓; fora de escopo (NAO_INTERESSA, `TelaCategoriaCompleta`) — nenhuma task toca nesses arquivos ✓.

**Placeholders:** nenhum "TBD"/"similar a"/passo sem código — todos os steps de código têm o bloco Kotlin completo.

**Consistência de tipos:** `TipoCategoria.QUERO_TER` e título `"Quero ter"` usados de forma idêntica nas Tasks 1 e 2; `aoTocarQueroTer: () -> Unit` segue exatamente a assinatura de `aoTocarMeusJogos`/`aoTocarFaltam` já existentes.
