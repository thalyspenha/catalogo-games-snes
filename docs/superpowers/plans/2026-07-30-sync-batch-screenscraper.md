# Sync em Batch do Catálogo SNES via ScreenScraper — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o seed local de 25 jogos por um sync em batch que baixa o catálogo completo real de SNES (1763 jogos únicos) do ScreenScraper e persiste no Room.

**Architecture:** Um DAT No-Intro é pré-processado offline por um módulo Kotlin JVM (`:ferramentas`) num asset JSON enxuto (`snes_catalogo_mestre.json`). Em runtime, `SincronizacaoRepository` percorre esse catálogo mestre, chama `jeuInfos.php` item a item (throttle 1,2s), mapeia com `ScreenScraperMapper` já existente, grava no Room e marca progresso numa tabela de checkpoint (`SincronizacaoStatusEntity`) que dá retomada e retry de falhas de graça. Uma tela dedicada (`TelaSincronizacao`) observa o progresso via `StateFlow`.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Room 2.6.1, Retrofit 2.11.0 + kotlinx.serialization, JUnit 4 (novo nesta plan — projeto não tinha testes até agora).

## Global Constraints

- Pacote base do app: `com.thalys.catalogosnes`. Sem framework de DI — todo singleton usa `companion object.obterInstancia(context)`, mesmo padrão de `NetworkModule`/`AppDatabase`/`JogoRepository`.
- Toda comunicação/comentário/nome de identificador em português do Brasil (pt-br), conforme `CLAUDE.md` do projeto.
- `compileSdk`/`targetSdk` = 35, `minSdk` = 26, `sourceCompatibility`/`jvmTarget` = 11 (não mudar nesta plan).
- Throttle entre chamadas ao ScreenScraper: exatamente 1200ms (`delay(1200)`), conforme recomendação da API documentada na spec.
- `ScreenScraperApi.SISTEMA_SNES` (valor 4) é o id de sistema confirmado — usar essa constante, nunca o literal.
- **Ambiente Linux deste projeto é NixOS**: `java`/`python3`/`node`/`kotlinc` não ficam no PATH direto, mas são alcançáveis (ex: `nix-shell -p <pacote> --run "<comando>"`, ou apontando direto pro caminho em `/nix/store`). Toda invocação de `./gradlew` precisa exportar `JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2` antes (confirmado funcionando: `Gradle 8.9` responde a `--version` com esse `JAVA_HOME`). Se esse caminho não existir mais no `/nix/store` de quem executa a plan, rodar `find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d` para achar o atual.
- O projeto não tinha nenhuma infraestrutura de teste antes desta plan (sem JUnit, sem `src/test`). Os testes desta plan cobrem só lógica pura (parsing/agrupamento/cálculo), porque escrever testes instrumentados de Room/Android (Robolectric etc.) é um investimento de infraestrutura separado, fora do escopo da spec.
- DAT de origem (arquivo do usuário, fora do repo): `/home/thalys/Downloads/Nintendo - Super Nintendo Entertainment System.dat`.

---

### Task 1: Permissão de INTERNET no manifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: nada.
- Produces: permissão de rede concedida ao app instalado — pré-requisito de runtime para toda chamada Retrofit feita pelas tasks seguintes (sem isso, `SincronizacaoRepository` falharia com `SecurityException` num dispositivo real, mesmo com o build passando).

- [ ] **Step 1: Adicionar a permissão**

Em `app/src/main/AndroidManifest.xml`, adicionar a tag `<uses-permission>` antes de `<application>`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
```

- [ ] **Step 2: Verificar que o build continua passando**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "fix: adiciona permissão de INTERNET, necessária pro sync com o ScreenScraper"
```

---

### Task 2: Suporte a testes JUnit no projeto

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nada.
- Produces: dependência `libs.junit` disponível para `testImplementation` em qualquer módulo do projeto (usada nas Tasks 3, 7, 8 e 9).

- [ ] **Step 1: Adicionar JUnit ao catálogo de versões**

Em `gradle/libs.versions.toml`, na seção `[versions]`, adicionar (ordem alfabética não é obrigatória no arquivo atual, adicionar ao final é suficiente):

```toml
junit = "4.13.2"
```

Na seção `[libraries]`, adicionar:

```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
```

- [ ] **Step 2: Adicionar a dependência de teste ao módulo `:app`**

Em `app/build.gradle.kts`, dentro do bloco `dependencies { ... }`, adicionar (pode ir logo após `implementation(libs.coil.compose)`):

```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 3: Verificar que o build continua passando**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: adiciona JUnit ao catálogo de versões e ao módulo :app"
```

---

### Task 3: Módulo `:ferramentas` — parser e agrupador do DAT (TDD)

**Files:**
- Create: `settings.gradle.kts` (modify — adicionar módulo)
- Create: `ferramentas/build.gradle.kts`
- Create: `ferramentas/src/main/kotlin/ferramentas/ItemCatalogoMestre.kt`
- Create: `ferramentas/src/main/kotlin/ferramentas/GerarCatalogoMestreSnes.kt`
- Test: `ferramentas/src/test/kotlin/ferramentas/AgrupamentoClonesTest.kt`

**Interfaces:**
- Consumes: `libs.junit`, `libs.plugins.kotlin.serialization` (já existem no catálogo de versões).
- Produces: `parsearDat(arquivo: File): List<JogoDat>`, `agruparEDeduplicar(jogos: List<JogoDat>): List<ItemCatalogoMestre>`, `data class ItemCatalogoMestre(val romNome: String, val crc: String, val romTamanho: Long, val nomeExibicao: String)` — usados pela Task 4 (executar a ferramenta contra o DAT real) e cujo *shape de saída* (`romNome`/`crc`/`romTamanho`/`nomeExibicao`) é espelhado por `CatalogoMestreItemDto` na Task 7.

- [ ] **Step 1: Registrar o módulo no `settings.gradle.kts`**

```kotlin
rootProject.name = "CatalogoGamesSnes"
include(":app")
include(":ferramentas")
```

- [ ] **Step 2: Adicionar o plugin `kotlin.jvm` ao catálogo de versões**

Em `gradle/libs.versions.toml`, na seção `[plugins]`, adicionar:

```toml
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 3: Criar `ferramentas/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("ferramentas.GerarCatalogoMestreSnesKt")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
```

(Sem bloco `repositories { }` aqui — o `settings.gradle.kts` já usa `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`, então os repositórios vêm só do `dependencyResolutionManagement` central, mesmo padrão do módulo `:app`.)

- [ ] **Step 4: Escrever o teste que falha primeiro**

Criar `ferramentas/src/test/kotlin/ferramentas/AgrupamentoClonesTest.kt`:

```kotlin
package ferramentas

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AgrupamentoClonesTest {

    private val xmlFixture = """
        <?xml version="1.0"?>
        <datafile>
          <game name="Standalone Game (World)" id="0001">
            <category>Games</category>
            <rom name="Standalone Game (World).sfc" size="1048576" crc="aaaaaaaa"/>
          </game>
          <game name="Grouped Game (Japan)" id="0002">
            <category>Games</category>
            <rom name="Grouped Game (Japan).sfc" size="2097152" crc="bbbbbbbb"/>
          </game>
          <game name="Grouped Game (USA)" id="0003" cloneofid="0002">
            <category>Games</category>
            <rom name="Grouped Game (USA).sfc" size="2097152" crc="cccccccc"/>
          </game>
          <game name="Beta Game (Europe) (Beta)" id="0004">
            <category>Games</category>
            <category>Preproduction</category>
            <rom name="Beta Game (Europe) (Beta).sfc" size="524288" crc="dddddddd"/>
          </game>
          <game name="Root Filtered (Prototype)" id="0005">
            <category>Preproduction</category>
            <rom name="Root Filtered (Prototype).sfc" size="1048576" crc="eeeeeeee"/>
          </game>
          <game name="Root Filtered (Release)" id="0006" cloneofid="0005">
            <category>Games</category>
            <rom name="Root Filtered (Release).sfc" size="1048576" crc="ffffffff"/>
          </game>
        </datafile>
    """.trimIndent()

    @Test
    fun `agrupa por cloneofid, escolhe raiz quando elegivel, exclui beta duplo-categorizado`() {
        val arquivoTemporario = File.createTempFile("fixture", ".dat")
        arquivoTemporario.writeText(xmlFixture)
        arquivoTemporario.deleteOnExit()

        val jogos = parsearDat(arquivoTemporario)
        val catalogo = agruparEDeduplicar(jogos)

        assertEquals(3, catalogo.size)
        assertEquals(
            listOf("Grouped Game (Japan)", "Root Filtered (Release)", "Standalone Game (World)"),
            catalogo.map { it.nomeExibicao },
        )
        assertEquals("bbbbbbbb", catalogo.first { it.nomeExibicao == "Grouped Game (Japan)" }.crc)
        assertEquals("ffffffff", catalogo.first { it.nomeExibicao == "Root Filtered (Release)" }.crc)
        assertEquals("aaaaaaaa", catalogo.first { it.nomeExibicao == "Standalone Game (World)" }.crc)
    }
}
```

Este teste cobre as três regras do algoritmo: (a) grupo com raiz elegível escolhe a raiz (`Grouped Game (Japan)` vence o clone `(USA)`), (b) grupo cuja raiz foi filtrada por categoria cai pro clone elegível (`Root Filtered (Release)`), (c) entrada com categorias `Games`+`Preproduction` é excluída inteiramente (`Beta Game` não aparece no resultado).

- [ ] **Step 5: Rodar o teste e confirmar que falha (funções ainda não existem)**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :ferramentas:test --tests "ferramentas.AgrupamentoClonesTest"
```
Expected: FAIL — erro de compilação, `parsearDat`/`agruparEDeduplicar`/`ItemCatalogoMestre` não existem ainda.

- [ ] **Step 6: Criar `ItemCatalogoMestre.kt`**

`ferramentas/src/main/kotlin/ferramentas/ItemCatalogoMestre.kt`:

```kotlin
package ferramentas

import kotlinx.serialization.Serializable

/** Um jogo único de SNES no catálogo mestre — o suficiente pra identificar no ScreenScraper. */
@Serializable
data class ItemCatalogoMestre(
    val romNome: String,
    val crc: String,
    val romTamanho: Long,
    val nomeExibicao: String,
)
```

- [ ] **Step 7: Implementar o parser e o agrupador**

`ferramentas/src/main/kotlin/ferramentas/GerarCatalogoMestreSnes.kt`:

```kotlin
package ferramentas

import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Uma entrada `<game>` do DAT No-Intro, antes do filtro de categoria/dedup. */
data class JogoDat(
    val id: String,
    val cloneOfId: String?,
    val categorias: Set<String>,
    val romNome: String,
    val crc: String,
    val romTamanho: Long,
    val nomeExibicao: String,
)

fun parsearDat(arquivo: File): List<JogoDat> {
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(arquivo)
    val gameNodes = doc.getElementsByTagName("game")

    val jogos = mutableListOf<JogoDat>()
    for (i in 0 until gameNodes.length) {
        val gameEl = gameNodes.item(i) as Element
        val id = gameEl.getAttribute("id")
        val cloneOfId = gameEl.getAttribute("cloneofid").ifBlank { null }
        val nomeExibicao = gameEl.getAttribute("name")

        val categorias = mutableSetOf<String>()
        val categoriaNodes = gameEl.getElementsByTagName("category")
        for (j in 0 until categoriaNodes.length) {
            categorias.add(categoriaNodes.item(j).textContent.trim())
        }

        val romNodes = gameEl.getElementsByTagName("rom")
        if (romNodes.length == 0) continue
        val romEl = romNodes.item(0) as Element
        val romNome = romEl.getAttribute("name")
        val crc = romEl.getAttribute("crc")
        val romTamanho = romEl.getAttribute("size").toLongOrNull() ?: continue

        jogos.add(JogoDat(id, cloneOfId, categorias, romNome, crc, romTamanho, nomeExibicao))
    }
    return jogos
}

/**
 * Filtra só entradas cujo conjunto de categorias é exatamente {"Games"} (exclui as que
 * também têm Preproduction/Demos — são beta/protótipo/demo, ver spec), agrupa por
 * cloneofid (ou o próprio id quando não é clone de nada) e escolhe 1 representante por
 * grupo: a raiz (sem cloneofid) se ela sobreviveu ao filtro, senão o de menor id entre
 * os clones elegíveis que sobraram.
 */
fun agruparEDeduplicar(jogos: List<JogoDat>): List<ItemCatalogoMestre> {
    val elegiveis = jogos.filter { it.categorias == setOf("Games") }
    val grupos = elegiveis.groupBy { it.cloneOfId ?: it.id }

    return grupos.values.map { membros ->
        val representante = membros.firstOrNull { it.cloneOfId == null }
            ?: membros.minBy { it.id.toInt() }
        ItemCatalogoMestre(
            romNome = representante.romNome,
            crc = representante.crc,
            romTamanho = representante.romTamanho,
            nomeExibicao = representante.nomeExibicao,
        )
    }.sortedBy { it.nomeExibicao }
}

fun main(args: Array<String>) {
    require(args.size == 2) {
        "Uso: gerarCatalogoMestre <caminho do .dat> <caminho do .json de saida>"
    }
    val jogos = parsearDat(File(args[0]))
    val catalogo = agruparEDeduplicar(jogos)
    val json = Json { prettyPrint = true }
    File(args[1]).writeText(json.encodeToString(catalogo))
    println("Gerado ${catalogo.size} jogos únicos em ${args[1]}")
}
```

- [ ] **Step 8: Rodar o teste e confirmar que passa**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :ferramentas:test --tests "ferramentas.AgrupamentoClonesTest"
```
Expected: PASS.

- [ ] **Step 9: Confirmar que o resto do projeto continua buildando**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml ferramentas/
git commit -m "feat: módulo :ferramentas com parser/agrupador do DAT No-Intro de SNES"
```

---

### Task 4: Gerar o catálogo mestre real e commitar o asset

**Files:**
- Create: `app/src/main/assets/snes_catalogo_mestre.json`

**Interfaces:**
- Consumes: `ferramentas.GerarCatalogoMestreSnes.main()` (Task 3), arquivo `/home/thalys/Downloads/Nintendo - Super Nintendo Entertainment System.dat`.
- Produces: `app/src/main/assets/snes_catalogo_mestre.json` — consumido por `CatalogoMestreLoader` na Task 7.

- [ ] **Step 1: Rodar a ferramenta contra o DAT real**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :ferramentas:run --args="'/home/thalys/Downloads/Nintendo - Super Nintendo Entertainment System.dat' 'app/src/main/assets/snes_catalogo_mestre.json'"
```
Expected: imprime `Gerado 1763 jogos únicos em app/src/main/assets/snes_catalogo_mestre.json` (número confirmado por análise manual do DAT durante o planejamento — ver spec). Se o número vier diferente, não seguir adiante sem entender por quê (pode indicar que o `.dat` do usuário mudou de versão, ou um bug no agrupamento).

- [ ] **Step 2: Conferir o arquivo gerado**

Run:
```bash
grep -c '"crc"' app/src/main/assets/snes_catalogo_mestre.json
grep -A1 '"nomeExibicao": "Super Mario World (USA)"' app/src/main/assets/snes_catalogo_mestre.json
grep -B3 '"nomeExibicao": "Chrono Trigger (USA)"' app/src/main/assets/snes_catalogo_mestre.json
```
Expected: contagem de `"crc"` bate com 1763; os dois jogos conhecidos aparecem no arquivo com `romNome`/`crc` preenchidos.

- [ ] **Step 3: Confirmar que o app builda com o novo asset**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/snes_catalogo_mestre.json
git commit -m "feat: gera catálogo mestre de SNES (1763 jogos) a partir do DAT No-Intro"
```

---

### Task 5: Room — status de sincronização e limpeza de tabelas

**Files:**
- Create: `app/src/main/java/com/thalys/catalogosnes/data/model/StatusSincronizacao.kt`
- Create: `app/src/main/java/com/thalys/catalogosnes/data/local/SincronizacaoStatusEntity.kt`
- Create: `app/src/main/java/com/thalys/catalogosnes/data/local/SincronizacaoStatusDao.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/data/local/JogoDao.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/data/local/PosseUsuarioDao.kt`

**Interfaces:**
- Consumes: nada de tasks anteriores.
- Produces: `SincronizacaoStatusEntity(crc, status, jogoId, mensagemErro)`, `StatusSincronizacao.{SUCESSO, FALHA}`, `SincronizacaoStatusDao.{salvar(status), buscarCrcsPorStatus(status = SUCESSO): List<String>, buscarFalhas(): List<SincronizacaoStatusEntity>, contarLinhas(): Int}`, `JogoDao.limparTudo()`, `PosseUsuarioDao.limparTudo()` — todos usados por `SincronizacaoRepository` na Task 9. `AppDatabase` (Task 6) referencia a entidade e o DAO daqui.

- [ ] **Step 1: Criar o enum de status**

`app/src/main/java/com/thalys/catalogosnes/data/model/StatusSincronizacao.kt`:

```kotlin
package com.thalys.catalogosnes.data.model

enum class StatusSincronizacao {
    SUCESSO,
    FALHA,
}
```

- [ ] **Step 2: Criar a entidade**

`app/src/main/java/com/thalys/catalogosnes/data/local/SincronizacaoStatusEntity.kt`:

```kotlin
package com.thalys.catalogosnes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.thalys.catalogosnes.data.model.StatusSincronizacao

/**
 * Registra a tentativa de sincronizar um jogo do catálogo mestre com o ScreenScraper. Uma
 * linha só existe depois de uma tentativa real — "pendente" é a ausência de linha para
 * aquele crc. É o checkpoint que permite retomar o sync e re-tentar só as falhas.
 */
@Entity(tableName = "sincronizacao_status")
data class SincronizacaoStatusEntity(
    @PrimaryKey val crc: String,
    val status: StatusSincronizacao,
    val jogoId: Long?,
    val mensagemErro: String?,
)
```

- [ ] **Step 3: Criar o DAO**

`app/src/main/java/com/thalys/catalogosnes/data/local/SincronizacaoStatusDao.kt`:

```kotlin
package com.thalys.catalogosnes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thalys.catalogosnes.data.model.StatusSincronizacao

@Dao
interface SincronizacaoStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(status: SincronizacaoStatusEntity)

    @Query("SELECT crc FROM sincronizacao_status WHERE status = :status")
    suspend fun buscarCrcsPorStatus(status: StatusSincronizacao = StatusSincronizacao.SUCESSO): List<String>

    @Query("SELECT * FROM sincronizacao_status WHERE status = 'FALHA'")
    suspend fun buscarFalhas(): List<SincronizacaoStatusEntity>

    @Query("SELECT COUNT(*) FROM sincronizacao_status")
    suspend fun contarLinhas(): Int
}
```

- [ ] **Step 4: Adicionar `limparTudo()` a `JogoDao` e `PosseUsuarioDao`**

Em `app/src/main/java/com/thalys/catalogosnes/data/local/JogoDao.kt`, adicionar dentro da interface:

```kotlin
    @Query("DELETE FROM jogos")
    suspend fun limparTudo()
```

Em `app/src/main/java/com/thalys/catalogosnes/data/local/PosseUsuarioDao.kt`, adicionar dentro da interface:

```kotlin
    @Query("DELETE FROM posse_usuario")
    suspend fun limparTudo()
```

- [ ] **Step 5: Verificar que o build passa**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL` (a entidade/DAO ainda não estão registrados no `AppDatabase`, então isso só confirma que o Kotlin/KSP compila; a Task 6 é que ativa o Room de fato).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/data/model/StatusSincronizacao.kt \
        app/src/main/java/com/thalys/catalogosnes/data/local/SincronizacaoStatusEntity.kt \
        app/src/main/java/com/thalys/catalogosnes/data/local/SincronizacaoStatusDao.kt \
        app/src/main/java/com/thalys/catalogosnes/data/local/JogoDao.kt \
        app/src/main/java/com/thalys/catalogosnes/data/local/PosseUsuarioDao.kt
git commit -m "feat: entidade/DAO de status de sincronização + limparTudo() nos DAOs existentes"
```

---

### Task 6: `AppDatabase` → version 2

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/data/local/AppDatabase.kt`

**Interfaces:**
- Consumes: `SincronizacaoStatusEntity`, `SincronizacaoStatusDao` (Task 5).
- Produces: `AppDatabase.sincronizacaoStatusDao(): SincronizacaoStatusDao` — usado por `SincronizacaoRepository` na Task 9.

- [ ] **Step 1: Registrar a entidade, subir a versão e adicionar o DAO abstrato**

Em `app/src/main/java/com/thalys/catalogosnes/data/local/AppDatabase.kt`, trocar:

```kotlin
@Database(
    entities = [JogoEntity::class, PosseUsuarioEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jogoDao(): JogoDao
    abstract fun posseUsuarioDao(): PosseUsuarioDao
```

por:

```kotlin
@Database(
    entities = [JogoEntity::class, PosseUsuarioEntity::class, SincronizacaoStatusEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jogoDao(): JogoDao
    abstract fun posseUsuarioDao(): PosseUsuarioDao
    abstract fun sincronizacaoStatusDao(): SincronizacaoStatusDao
```

- [ ] **Step 2: Adicionar a migração 1→2**

Ainda em `AppDatabase.kt`, adicionar o import `androidx.room.migration.Migration` e, dentro do `companion object`, antes de `fun obterInstancia`:

```kotlin
        private val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sincronizacao_status (
                        crc TEXT NOT NULL PRIMARY KEY,
                        status TEXT NOT NULL,
                        jogoId INTEGER,
                        mensagemErro TEXT
                    )
                    """.trimIndent()
                )
            }
        }
```

- [ ] **Step 3: Registrar a migração no builder**

Em `construir(context)`, trocar:

```kotlin
            db = Room.databaseBuilder(context, AppDatabase::class.java, NOME_BANCO)
                .addCallback(callbackDeSeed)
                .build()
```

por:

```kotlin
            db = Room.databaseBuilder(context, AppDatabase::class.java, NOME_BANCO)
                .addCallback(callbackDeSeed)
                .addMigrations(MIGRACAO_1_2)
                .build()
```

- [ ] **Step 4: Verificar que o build passa**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/data/local/AppDatabase.kt
git commit -m "feat: AppDatabase version 2, com migração pra tabela de status de sincronização"
```

---

### Task 7: `CatalogoMestreItemDto` + `CatalogoMestreLoader` (com teste)

**Files:**
- Create: `app/src/main/java/com/thalys/catalogosnes/data/sync/CatalogoMestreItemDto.kt`
- Create: `app/src/main/java/com/thalys/catalogosnes/data/sync/CatalogoMestreLoader.kt`
- Test: `app/src/test/java/com/thalys/catalogosnes/data/sync/CatalogoMestreLoaderTest.kt`

**Interfaces:**
- Consumes: `app/src/main/assets/snes_catalogo_mestre.json` (Task 4).
- Produces: `data class CatalogoMestreItemDto(val romNome: String, val crc: String, val romTamanho: Long, val nomeExibicao: String)`, `CatalogoMestreLoader.carregar(context: Context): List<CatalogoMestreItemDto>`, `CatalogoMestreLoader.parsear(conteudoJson: String): List<CatalogoMestreItemDto>` — usados pela Task 8 (`calcularRestante`) e Task 9 (`SincronizacaoRepository`).

- [ ] **Step 1: Escrever o teste que falha primeiro**

`app/src/test/java/com/thalys/catalogosnes/data/sync/CatalogoMestreLoaderTest.kt`:

```kotlin
package com.thalys.catalogosnes.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogoMestreLoaderTest {

    @Test
    fun `parseia lista de itens do catalogo mestre a partir do JSON`() {
        val jsonDeTeste = """
            [
              {
                "romNome": "Super Mario World (USA).sfc",
                "crc": "b19ed489",
                "romTamanho": 524288,
                "nomeExibicao": "Super Mario World (USA)"
              }
            ]
        """.trimIndent()

        val resultado = CatalogoMestreLoader.parsear(jsonDeTeste)

        assertEquals(1, resultado.size)
        assertEquals("b19ed489", resultado.first().crc)
        assertEquals("Super Mario World (USA)", resultado.first().nomeExibicao)
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew testDebugUnitTest --tests "com.thalys.catalogosnes.data.sync.CatalogoMestreLoaderTest"
```
Expected: FAIL — `CatalogoMestreLoader`/`CatalogoMestreItemDto` não existem ainda.

- [ ] **Step 3: Criar o DTO**

`app/src/main/java/com/thalys/catalogosnes/data/sync/CatalogoMestreItemDto.kt`:

```kotlin
package com.thalys.catalogosnes.data.sync

import kotlinx.serialization.Serializable

/**
 * Espelha uma entrada de `assets/snes_catalogo_mestre.json` (gerado pelo módulo
 * :ferramentas a partir do DAT No-Intro) — um jogo único de SNES, identificado por
 * nome/crc/tamanho de ROM pra consultar o ScreenScraper via jeuInfos.php.
 */
@Serializable
data class CatalogoMestreItemDto(
    val romNome: String,
    val crc: String,
    val romTamanho: Long,
    val nomeExibicao: String,
)
```

- [ ] **Step 4: Criar o loader**

`app/src/main/java/com/thalys/catalogosnes/data/sync/CatalogoMestreLoader.kt`:

```kotlin
package com.thalys.catalogosnes.data.sync

import android.content.Context
import kotlinx.serialization.json.Json

/** Lê `assets/snes_catalogo_mestre.json`, mesmo padrão do `SeedLoader` para o seed antigo. */
object CatalogoMestreLoader {

    private const val ARQUIVO_CATALOGO_MESTRE = "snes_catalogo_mestre.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun carregar(context: Context): List<CatalogoMestreItemDto> {
        val conteudo = context.assets.open(ARQUIVO_CATALOGO_MESTRE).bufferedReader().use { it.readText() }
        return parsear(conteudo)
    }

    fun parsear(conteudoJson: String): List<CatalogoMestreItemDto> =
        json.decodeFromString(conteudoJson)
}
```

- [ ] **Step 5: Rodar e confirmar que passa**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew testDebugUnitTest --tests "com.thalys.catalogosnes.data.sync.CatalogoMestreLoaderTest"
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/data/sync/CatalogoMestreItemDto.kt \
        app/src/main/java/com/thalys/catalogosnes/data/sync/CatalogoMestreLoader.kt \
        app/src/test/java/com/thalys/catalogosnes/data/sync/CatalogoMestreLoaderTest.kt
git commit -m "feat: DTO e loader do catálogo mestre (assets/snes_catalogo_mestre.json)"
```

---

### Task 8: `calcularRestante()` (TDD)

**Files:**
- Create: `app/src/main/java/com/thalys/catalogosnes/data/sync/CalculoRestante.kt`
- Test: `app/src/test/java/com/thalys/catalogosnes/data/sync/CalculoRestanteTest.kt`

**Interfaces:**
- Consumes: `CatalogoMestreItemDto` (Task 7).
- Produces: `fun calcularRestante(catalogoMestre: List<CatalogoMestreItemDto>, crcsComSucesso: Set<String>): List<CatalogoMestreItemDto>` — usada por `SincronizacaoRepository` na Task 9.

- [ ] **Step 1: Escrever os testes que falham primeiro**

`app/src/test/java/com/thalys/catalogosnes/data/sync/CalculoRestanteTest.kt`:

```kotlin
package com.thalys.catalogosnes.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculoRestanteTest {

    private val item1 = CatalogoMestreItemDto("A.sfc", "crc-a", 1024, "Jogo A")
    private val item2 = CatalogoMestreItemDto("B.sfc", "crc-b", 2048, "Jogo B")
    private val item3 = CatalogoMestreItemDto("C.sfc", "crc-c", 4096, "Jogo C")
    private val catalogo = listOf(item1, item2, item3)

    @Test
    fun `primeira execucao, sem sucesso gravado, retorna catalogo inteiro`() {
        val restante = calcularRestante(catalogo, crcsComSucesso = emptySet())
        assertEquals(listOf(item1, item2, item3), restante)
    }

    @Test
    fun `retomada parcial, exclui os ja marcados como sucesso`() {
        val restante = calcularRestante(catalogo, crcsComSucesso = setOf("crc-a"))
        assertEquals(listOf(item2, item3), restante)
    }

    @Test
    fun `tudo sincronizado, retorna lista vazia`() {
        val restante = calcularRestante(catalogo, crcsComSucesso = setOf("crc-a", "crc-b", "crc-c"))
        assertEquals(emptyList<CatalogoMestreItemDto>(), restante)
    }

    @Test
    fun `item com falha registrada nao esta em sucesso, entao continua no restante para nova tentativa`() {
        // status FALHA nunca entra no conjunto de crcsComSucesso passado pelo repository,
        // então do ponto de vista deste cálculo é indistinguível de "nunca tentado".
        val restante = calcularRestante(catalogo, crcsComSucesso = setOf("crc-b"))
        assertEquals(listOf(item1, item3), restante)
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew testDebugUnitTest --tests "com.thalys.catalogosnes.data.sync.CalculoRestanteTest"
```
Expected: FAIL — `calcularRestante` não existe ainda.

- [ ] **Step 3: Implementar**

`app/src/main/java/com/thalys/catalogosnes/data/sync/CalculoRestante.kt`:

```kotlin
package com.thalys.catalogosnes.data.sync

/**
 * Itens do catálogo mestre que ainda não têm status SUCESSO gravado — usado tanto pra
 * retomar um sync interrompido quanto pra re-tentar só os que falharam (falhas não têm
 * SUCESSO gravado, então sempre voltam a aparecer aqui).
 */
fun calcularRestante(
    catalogoMestre: List<CatalogoMestreItemDto>,
    crcsComSucesso: Set<String>,
): List<CatalogoMestreItemDto> =
    catalogoMestre.filter { it.crc !in crcsComSucesso }
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew testDebugUnitTest --tests "com.thalys.catalogosnes.data.sync.CalculoRestanteTest"
```
Expected: PASS (4 testes).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/data/sync/CalculoRestante.kt \
        app/src/test/java/com/thalys/catalogosnes/data/sync/CalculoRestanteTest.kt
git commit -m "feat: calcularRestante — catálogo mestre menos crcs já sincronizados"
```

---

### Task 9: `SincronizacaoEstado` + `SincronizacaoRepository`

**Files:**
- Create: `app/src/main/java/com/thalys/catalogosnes/data/sync/SincronizacaoEstado.kt`
- Create: `app/src/main/java/com/thalys/catalogosnes/data/sync/SincronizacaoRepository.kt`
- Test: `app/src/test/java/com/thalys/catalogosnes/data/sync/CotaEsgotadaTest.kt`

**Interfaces:**
- Consumes: `CatalogoMestreLoader`, `calcularRestante` (Tasks 7-8); `JogoDao`, `PosseUsuarioDao`, `SincronizacaoStatusDao`, `AppDatabase.obterInstancia` (Tasks 5-6); `ScreenScraperApi`, `ScreenScraperCredenciais`, `NetworkModule`, `ScreenScraperMapper` (já existentes).
- Produces: `sealed class SincronizacaoEstado { Ocioso, EmAndamento(atual, total, nomeJogoAtual), Concluido(sucesso, falhas: List<FalhaSincronizacao>), CotaEsgotada(sucesso, restantes) }`, `data class FalhaSincronizacao(val nomeExibicao: String, val motivo: String)`, `SincronizacaoRepository.estado: StateFlow<SincronizacaoEstado>`, `suspend fun SincronizacaoRepository.sincronizar()`, `SincronizacaoRepository.obterInstancia(context): SincronizacaoRepository` — usados por `SincronizacaoViewModel` na Task 10.

- [ ] **Step 1: Criar os estados**

`app/src/main/java/com/thalys/catalogosnes/data/sync/SincronizacaoEstado.kt`:

```kotlin
package com.thalys.catalogosnes.data.sync

sealed class SincronizacaoEstado {
    data object Ocioso : SincronizacaoEstado()
    data class EmAndamento(val atual: Int, val total: Int, val nomeJogoAtual: String) : SincronizacaoEstado()
    data class Concluido(val sucesso: Int, val falhas: List<FalhaSincronizacao>) : SincronizacaoEstado()
    data class CotaEsgotada(val sucesso: Int, val restantes: Int) : SincronizacaoEstado()
}

data class FalhaSincronizacao(val nomeExibicao: String, val motivo: String)
```

- [ ] **Step 2: Escrever o teste da heurística de cota esgotada (falha primeiro)**

`app/src/test/java/com/thalys/catalogosnes/data/sync/CotaEsgotadaTest.kt`:

```kotlin
package com.thalys.catalogosnes.data.sync

import com.thalys.catalogosnes.data.remote.screenscraper.dto.HeaderDto
import com.thalys.catalogosnes.data.remote.screenscraper.dto.JogoInfoRespostaDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CotaEsgotadaTest {

    @Test
    fun `detecta erro mencionando quota no header`() {
        val resposta = JogoInfoRespostaDto(header = HeaderDto(error = "Quota Atteinte"), response = null)
        assertTrue(cotaEsgotada(resposta))
    }

    @Test
    fun `nao detecta cota quando nao ha erro`() {
        val resposta = JogoInfoRespostaDto(header = HeaderDto(error = null), response = null)
        assertFalse(cotaEsgotada(resposta))
    }

    @Test
    fun `nao detecta cota em erro nao relacionado`() {
        val resposta = JogoInfoRespostaDto(header = HeaderDto(error = "Erreur de login"), response = null)
        assertFalse(cotaEsgotada(resposta))
    }
}
```

- [ ] **Step 3: Rodar e confirmar que falha**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew testDebugUnitTest --tests "com.thalys.catalogosnes.data.sync.CotaEsgotadaTest"
```
Expected: FAIL — `cotaEsgotada` não existe ainda.

- [ ] **Step 4: Implementar o repositório**

`app/src/main/java/com/thalys/catalogosnes/data/sync/SincronizacaoRepository.kt`:

```kotlin
package com.thalys.catalogosnes.data.sync

import android.content.Context
import com.thalys.catalogosnes.data.local.AppDatabase
import com.thalys.catalogosnes.data.local.JogoDao
import com.thalys.catalogosnes.data.local.PosseUsuarioDao
import com.thalys.catalogosnes.data.local.SincronizacaoStatusDao
import com.thalys.catalogosnes.data.local.SincronizacaoStatusEntity
import com.thalys.catalogosnes.data.model.StatusSincronizacao
import com.thalys.catalogosnes.data.remote.screenscraper.NetworkModule
import com.thalys.catalogosnes.data.remote.screenscraper.ScreenScraperApi
import com.thalys.catalogosnes.data.remote.screenscraper.ScreenScraperCredenciais
import com.thalys.catalogosnes.data.remote.screenscraper.ScreenScraperMapper
import com.thalys.catalogosnes.data.remote.screenscraper.dto.JeuDto
import com.thalys.catalogosnes.data.remote.screenscraper.dto.JogoInfoRespostaDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext

private const val THROTTLE_MS = 1200L
private const val MAX_TENTATIVAS_REDE = 3
private val BACKOFF_MS = listOf(2000L, 4000L)

class SincronizacaoRepository(
    private val context: Context,
    private val jogoDao: JogoDao,
    private val posseUsuarioDao: PosseUsuarioDao,
    private val sincronizacaoStatusDao: SincronizacaoStatusDao,
    private val screenScraperApi: ScreenScraperApi,
) {

    private val _estado = MutableStateFlow<SincronizacaoEstado>(SincronizacaoEstado.Ocioso)
    val estado: StateFlow<SincronizacaoEstado> = _estado.asStateFlow()

    suspend fun sincronizar() {
        val catalogoMestre = CatalogoMestreLoader.carregar(context)
        val totalLinhasStatus = sincronizacaoStatusDao.contarLinhas()
        if (totalLinhasStatus == 0) {
            jogoDao.limparTudo()
            posseUsuarioDao.limparTudo()
        }

        val crcsComSucesso = sincronizacaoStatusDao.buscarCrcsPorStatus(StatusSincronizacao.SUCESSO).toSet()
        val restante = calcularRestante(catalogoMestre, crcsComSucesso)
        val total = catalogoMestre.size
        var sucesso = crcsComSucesso.size

        for ((indice, item) in restante.withIndex()) {
            coroutineContext.ensureActive()
            delay(THROTTLE_MS)

            _estado.value = SincronizacaoEstado.EmAndamento(
                atual = sucesso + indice + 1,
                total = total,
                nomeJogoAtual = item.nomeExibicao,
            )

            when (val resultado = buscarComRetry(item)) {
                is ResultadoBusca.Sucesso -> {
                    val jogoEntity = ScreenScraperMapper.paraJogoEntity(resultado.jeu)
                    if (jogoEntity == null) {
                        sincronizacaoStatusDao.salvar(
                            SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, "Resposta sem id/nome válidos")
                        )
                    } else {
                        jogoDao.inserirTodos(listOf(jogoEntity))
                        sincronizacaoStatusDao.salvar(
                            SincronizacaoStatusEntity(item.crc, StatusSincronizacao.SUCESSO, jogoEntity.id, null)
                        )
                        sucesso++
                    }
                }
                is ResultadoBusca.NaoEncontrado -> {
                    sincronizacaoStatusDao.salvar(
                        SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, "Jogo não encontrado no ScreenScraper")
                    )
                }
                is ResultadoBusca.CotaEsgotada -> {
                    _estado.value = SincronizacaoEstado.CotaEsgotada(sucesso = sucesso, restantes = total - sucesso)
                    return
                }
                is ResultadoBusca.ErroDeRede -> {
                    sincronizacaoStatusDao.salvar(
                        SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, resultado.mensagem)
                    )
                }
            }
        }

        val statusFalhas = sincronizacaoStatusDao.buscarFalhas()
        val nomesPorCrc = catalogoMestre.associateBy { it.crc }
        val falhasDetalhadas = statusFalhas.map { falha ->
            FalhaSincronizacao(
                nomeExibicao = nomesPorCrc[falha.crc]?.nomeExibicao ?: falha.crc,
                motivo = falha.mensagemErro ?: "Erro desconhecido",
            )
        }
        _estado.value = SincronizacaoEstado.Concluido(sucesso = sucesso, falhas = falhasDetalhadas)
    }

    private suspend fun buscarComRetry(item: CatalogoMestreItemDto): ResultadoBusca {
        var ultimoErro: String? = null
        repeat(MAX_TENTATIVAS_REDE) { tentativa ->
            try {
                val resposta = screenScraperApi.buscarInfoJogo(
                    devId = ScreenScraperCredenciais.devId,
                    devPassword = ScreenScraperCredenciais.devPassword,
                    softName = ScreenScraperCredenciais.softName,
                    ssid = ScreenScraperCredenciais.usuarioId.ifBlank { null },
                    sspassword = ScreenScraperCredenciais.usuarioSenha.ifBlank { null },
                    systemeId = ScreenScraperApi.SISTEMA_SNES,
                    romNome = item.romNome,
                    romTamanho = item.romTamanho,
                    crc = item.crc,
                )
                if (cotaEsgotada(resposta)) return ResultadoBusca.CotaEsgotada
                val jeu = resposta.response?.jeu ?: return ResultadoBusca.NaoEncontrado
                return ResultadoBusca.Sucesso(jeu)
            } catch (e: Exception) {
                ultimoErro = e.message ?: e.javaClass.simpleName
                if (tentativa < MAX_TENTATIVAS_REDE - 1) delay(BACKOFF_MS[tentativa])
            }
        }
        return ResultadoBusca.ErroDeRede(ultimoErro ?: "Erro de rede desconhecido")
    }

    companion object {
        @Volatile
        private var instancia: SincronizacaoRepository? = null

        fun obterInstancia(context: Context): SincronizacaoRepository {
            return instancia ?: synchronized(this) {
                instancia ?: run {
                    val banco = AppDatabase.obterInstancia(context.applicationContext)
                    SincronizacaoRepository(
                        context = context.applicationContext,
                        jogoDao = banco.jogoDao(),
                        posseUsuarioDao = banco.posseUsuarioDao(),
                        sincronizacaoStatusDao = banco.sincronizacaoStatusDao(),
                        screenScraperApi = NetworkModule.screenScraperApi,
                    ).also { instancia = it }
                }
            }
        }
    }
}

/**
 * Heurística best-effort pra detectar cota diária esgotada pelo texto de erro do header.
 * O texto exato ainda não foi observado com uma cota real estourada (ver spec) — ajustar
 * aqui se, na prática, a API sinalizar isso de outro jeito.
 */
internal fun cotaEsgotada(resposta: JogoInfoRespostaDto): Boolean {
    val erro = resposta.header?.error?.lowercase() ?: return false
    return "quota" in erro || "limite" in erro
}

private sealed class ResultadoBusca {
    data class Sucesso(val jeu: JeuDto) : ResultadoBusca()
    data object NaoEncontrado : ResultadoBusca()
    data object CotaEsgotada : ResultadoBusca()
    data class ErroDeRede(val mensagem: String) : ResultadoBusca()
}
```

- [ ] **Step 5: Rodar o teste de cota e confirmar que passa**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew testDebugUnitTest --tests "com.thalys.catalogosnes.data.sync.CotaEsgotadaTest"
```
Expected: PASS (3 testes).

- [ ] **Step 6: Confirmar que o projeto inteiro builda**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/data/sync/SincronizacaoEstado.kt \
        app/src/main/java/com/thalys/catalogosnes/data/sync/SincronizacaoRepository.kt \
        app/src/test/java/com/thalys/catalogosnes/data/sync/CotaEsgotadaTest.kt
git commit -m "feat: SincronizacaoRepository — orquestra o sync em batch com o ScreenScraper"
```

---

### Task 10: `SincronizacaoViewModel`

**Files:**
- Create: `app/src/main/java/com/thalys/catalogosnes/ui/sincronizacao/SincronizacaoViewModel.kt`

**Interfaces:**
- Consumes: `SincronizacaoRepository.obterInstancia`, `.estado`, `.sincronizar()` (Task 9).
- Produces: `SincronizacaoViewModel.estado: StateFlow<SincronizacaoEstado>`, `.iniciar()`, `.cancelar()`, `SincronizacaoViewModel.Factory(context)` — usados por `TelaSincronizacao` na Task 11.

- [ ] **Step 1: Implementar**

`app/src/main/java/com/thalys/catalogosnes/ui/sincronizacao/SincronizacaoViewModel.kt`:

```kotlin
package com.thalys.catalogosnes.ui.sincronizacao

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thalys.catalogosnes.data.sync.SincronizacaoEstado
import com.thalys.catalogosnes.data.sync.SincronizacaoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Expõe o progresso do `SincronizacaoRepository` pra `TelaSincronizacao` e controla
 * início/cancelamento. Cancelar só interrompe o coroutine — o progresso já gravado no
 * Room continua valendo como checkpoint pra retomar depois.
 */
class SincronizacaoViewModel(
    private val repository: SincronizacaoRepository,
) : ViewModel() {

    val estado: StateFlow<SincronizacaoEstado> = repository.estado

    private var jobSincronizacao: Job? = null

    fun iniciar() {
        if (jobSincronizacao?.isActive == true) return
        jobSincronizacao = viewModelScope.launch {
            repository.sincronizar()
        }
    }

    fun cancelar() {
        jobSincronizacao?.cancel()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = SincronizacaoRepository.obterInstancia(context.applicationContext)
            return SincronizacaoViewModel(repository) as T
        }
    }
}
```

- [ ] **Step 2: Verificar que o build passa**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/sincronizacao/SincronizacaoViewModel.kt
git commit -m "feat: SincronizacaoViewModel"
```

---

### Task 11: `TelaSincronizacao` (Compose)

**Files:**
- Create: `app/src/main/java/com/thalys/catalogosnes/ui/sincronizacao/TelaSincronizacao.kt`

**Interfaces:**
- Consumes: `SincronizacaoViewModel` (Task 10), `SincronizacaoEstado`/`FalhaSincronizacao` (Task 9).
- Produces: `@Composable fun TelaSincronizacao(aoVoltar: () -> Unit, viewModel: SincronizacaoViewModel = viewModel(...))` — usado por `CatalogoNavHost` na Task 12.

- [ ] **Step 1: Implementar**

`app/src/main/java/com/thalys/catalogosnes/ui/sincronizacao/TelaSincronizacao.kt`:

```kotlin
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
                        progress = { estadoAtual.atual.toFloat() / estadoAtual.total.toFloat() },
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
                    Text("Cota diária da API do ScreenScraper esgotada. Tente novamente mais tarde.")
                    Text("${estadoAtual.sucesso} sincronizados até agora, ${estadoAtual.restantes} restantes.")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verificar que o build passa**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/sincronizacao/TelaSincronizacao.kt
git commit -m "feat: TelaSincronizacao — progresso, cancelar e retry de falhas"
```

---

### Task 12: Navegação — ligar a tela de sync na biblioteca

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/navigation/CatalogoNavHost.kt`

**Interfaces:**
- Consumes: `TelaSincronizacao` (Task 11).
- Produces: rota `"sincronizacao"` navegável a partir de um ícone na `TelaBiblioteca`.

- [ ] **Step 1: Adicionar o parâmetro e o ícone na `TelaBiblioteca`**

Em `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt`, adicionar imports:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
```

Trocar a assinatura da função:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaBiblioteca(
    aoClicarJogo: (Long) -> Unit,
    aoClicarSincronizar: () -> Unit,
    viewModel: BibliotecaViewModel = viewModel(
        factory = BibliotecaViewModel.Factory(LocalContext.current)
    ),
) {
```

Trocar o `topBar`:

```kotlin
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
```

Atualizar o preview no final do arquivo:

```kotlin
@Preview(showBackground = true)
@Composable
private fun PreviewTelaBiblioteca() {
    CatalogoSnesTheme {
        TelaBiblioteca(aoClicarJogo = {}, aoClicarSincronizar = {})
    }
}
```

- [ ] **Step 2: Registrar a rota no `CatalogoNavHost`**

Em `app/src/main/java/com/thalys/catalogosnes/ui/navigation/CatalogoNavHost.kt`, adicionar o import:

```kotlin
import com.thalys.catalogosnes.ui.sincronizacao.TelaSincronizacao
```

Adicionar a constante de rota:

```kotlin
private const val ROTA_BIBLIOTECA = "biblioteca"
private const val ARGUMENTO_JOGO_ID = "jogoId"
private const val ROTA_DETALHE = "detalhe/{$ARGUMENTO_JOGO_ID}"
private const val ROTA_SINCRONIZACAO = "sincronizacao"
```

Atualizar o `composable(ROTA_BIBLIOTECA)` e adicionar o novo destino:

```kotlin
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
```

- [ ] **Step 3: Build final completo, todos os testes**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew testDebugUnitTest :ferramentas:test assembleDebug
```
Expected: `BUILD SUCCESSFUL`, todos os testes das Tasks 3, 7, 8 e 9 passando.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt \
        app/src/main/java/com/thalys/catalogosnes/ui/navigation/CatalogoNavHost.kt
git commit -m "feat: navegação pra tela de sincronização a partir da biblioteca"
```

---

## Verificação manual (fora do escopo automatizado)

Depois da Task 12, rodar manualmente num dispositivo/emulador (não coberto por teste automatizado, por não haver infraestrutura de teste instrumentado no projeto):

1. Instalar o app, abrir a biblioteca (deve mostrar o seed de 25 jogos), tocar no ícone de sincronizar.
2. Deixar rodar uns 10-15 jogos, tocar em "Cancelar".
3. Fechar e reabrir o app, voltar pra tela de sincronização, tocar em "Iniciar sincronização" de novo — confirmar que o contador não recomeça do zero (os jogos já sincronizados não são refeitos).
4. Ao concluir, conferir que a biblioteca mostra jogos reais (nome/capa/descrição vindos do ScreenScraper), não mais os 25 do seed.
