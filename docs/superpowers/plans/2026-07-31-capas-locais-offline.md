# Capas locais (offline) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Baixar e persistir a capa de cada jogo como arquivo local durante a sincronização, pra biblioteca funcionar de verdade offline — hoje as capas só existem como URL remota, e mesmo com o cache em disco do Coil populado, o ScreenScraper manda `Cache-Control: no-cache, must-revalidate`, o que obriga o Coil a confirmar com o servidor antes de reusar o cache. Sem internet, essa confirmação falha e a capa não aparece, mesmo já tendo sido baixada antes.

**Architecture:** `JogoEntity` ganha um campo novo `caminhoCapaLocal: String?` (caminho de arquivo, não URL). Durante a sincronização, depois de mapear a resposta do ScreenScraper pra `JogoEntity`, um novo `CapaDownloader` baixa os bytes da capa (reaproveitando o `OkHttpClient` já configurado em `NetworkModule`) e grava em armazenamento privado do app (`context.filesDir/capas/<id>.jpg`); o caminho do arquivo vai pro banco junto com o resto dos metadados do jogo, na mesma linha. A UI passa a mostrar `caminhoCapaLocal` (arquivo local, funciona offline) quando existir, caindo pra `urlCapa` (melhor esforço, exige rede) só quando não existir — cobre as linhas já sincronizadas antes desta mudança sem forçar re-sync completo.

**Tech Stack:** Kotlin, Room (migração de schema), OkHttp (reaproveitado do Retrofit), Coil (consumo do arquivo local via `AsyncImage`), JUnit (não se aplica às tasks desta feature — ver Global Constraints).

## Global Constraints

- Capas baixadas ficam em armazenamento privado do app (`context.filesDir/capas/<id>.jpg`) — não usa armazenamento externo/compartilhado, não precisa de permissão de storage.
- Download de capa é best-effort: se falhar, o jogo é salvo mesmo assim (metadados são mais importantes que a capa), com `caminhoCapaLocal = null`. Não retenta o download separadamente do resto do fluxo de sincronização.
- `caminhoCapaLocal` é uma coluna nova, nullable, adicionada via migração real do Room (`Migration(2, 3)`) — sem `fallbackToDestructiveMigration`, sem apagar dado existente.
- UI usa `caminhoCapaLocal` quando presente; cai pra `urlCapa` (exige rede) só quando `caminhoCapaLocal` for null — cobre jogos sincronizados antes desta mudança sem quebrar o que já funcionava (online).
- Sem teste automatizado pra código que toca rede/filesystem real (mesmo padrão já estabelecido pra `SincronizacaoRepository`, que também não tem teste JUnit) — verificação é por build bem-sucedido + teste manual no device físico (Task 4).
- Ambiente Linux NixOS: `export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2` antes de qualquer `./gradlew` (se o caminho não existir mais, `find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d` acha o atual).

---

### Task 1: Schema — `caminhoCapaLocal` em `JogoEntity` + migração pro banco versão 3

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/data/local/JogoEntity.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/data/local/AppDatabase.kt`

**Interfaces:**
- Produces: `JogoEntity.caminhoCapaLocal: String?` (novo campo, default `null`) — usado pela Task 2 (gravado após o download) e pela Task 3 (lido pela UI).

Refatoração de schema pura — sem teste automatizado (ver Global Constraints); verificação é por compilação bem-sucedida.

- [ ] **Step 1: Adicionar o campo `caminhoCapaLocal` em `JogoEntity`**

Substituir o conteúdo de `JogoEntity.kt` inteiro por:

```kotlin
package com.thalys.catalogosnes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadados do catálogo (biblioteca completa de jogos de SNES), vindos do ScreenScraper.
 */
@Entity(tableName = "jogos")
data class JogoEntity(
    @PrimaryKey val id: Long,
    val nome: String,
    val descricao: String?,
    val anoLancamento: Int?,
    val genero: String?,
    val desenvolvedora: String?,
    val publicadora: String?,
    val urlCapa: String?,
    val regiao: String?,
    val caminhoCapaLocal: String? = null,
)
```

O default `= null` mantém compatíveis as construções existentes de `JogoEntity` que não passam esse parâmetro (`ScreenScraperMapper.paraJogoEntity` e `SeedLoader`) — nenhuma delas precisa mudar nesta task.

- [ ] **Step 2: Adicionar a migração 2→3 e subir a versão do banco em `AppDatabase.kt`**

Em `AppDatabase.kt`, trocar `version = 2` por `version = 3` no `@Database(...)`:

```kotlin
@Database(
    entities = [JogoEntity::class, PosseUsuarioEntity::class, SincronizacaoStatusEntity::class],
    version = 3,
    exportSchema = false,
)
```

Adicionar, logo depois de `MIGRACAO_1_2`:

```kotlin
        private val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jogos ADD COLUMN caminhoCapaLocal TEXT")
            }
        }
```

E trocar `.addMigrations(MIGRACAO_1_2)` por:

```kotlin
                .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3)
```

- [ ] **Step 3: Compilar**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/data/local/JogoEntity.kt app/src/main/java/com/thalys/catalogosnes/data/local/AppDatabase.kt
git commit -m "feat: JogoEntity ganha caminhoCapaLocal, banco sobe pra versão 3"
```

---

### Task 2: `CapaDownloader` + wiring no `SincronizacaoRepository`

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/data/remote/screenscraper/NetworkModule.kt`
- Create: `app/src/main/java/com/thalys/catalogosnes/data/sync/CapaDownloader.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/data/sync/SincronizacaoRepository.kt`

**Interfaces:**
- Consumes: `JogoEntity.caminhoCapaLocal` (Task 1), `NetworkModule.okHttpClient` (este task, torna público).
- Produces: `CapaDownloader.baixar(context: Context, okHttpClient: OkHttpClient, jogoId: Long, url: String): String?` — retorna o caminho absoluto do arquivo salvo, ou `null` se o download falhar. Usado só pelo `SincronizacaoRepository` (Step 3 deste task).

Sem teste automatizado (toca rede + filesystem reais, ver Global Constraints); verificação é por compilação + Task 4.

- [ ] **Step 1: Tornar `okHttpClient` público em `NetworkModule.kt`**

Em `NetworkModule.kt`, trocar:

```kotlin
    private val okHttpClient: OkHttpClient by lazy {
```

por:

```kotlin
    val okHttpClient: OkHttpClient by lazy {
```

(só remove o `private` — o resto do bloco continua igual.)

- [ ] **Step 2: Criar `CapaDownloader.kt`**

```kotlin
package com.thalys.catalogosnes.data.sync

import android.content.Context
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Baixa a capa de [url] e salva em armazenamento privado do app (`filesDir/capas/<jogoId>.jpg`).
 * Necessário porque o ScreenScraper manda as capas com `Cache-Control: no-cache,
 * must-revalidate` — o cache em disco do Coil sozinho não é suficiente pra funcionar offline,
 * já que o Coil é obrigado a confirmar com o servidor antes de reusar o arquivo cacheado.
 *
 * Retorna `null` em qualquer falha (rede, HTTP, disco) — o chamador decide o que fazer; o
 * jogo continua sendo salvo com os metadados mesmo sem capa local.
 */
object CapaDownloader {

    fun baixar(context: Context, okHttpClient: OkHttpClient, jogoId: Long, url: String): String? {
        return try {
            val resposta = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
            resposta.use {
                if (!it.isSuccessful) return null
                val bytes = it.body?.bytes() ?: return null
                val diretorio = File(context.filesDir, "capas").apply { mkdirs() }
                val arquivo = File(diretorio, "$jogoId.jpg")
                arquivo.writeBytes(bytes)
                arquivo.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}
```

- [ ] **Step 3: Chamar o `CapaDownloader` no `SincronizacaoRepository`, no branch de sucesso da sincronização**

Em `SincronizacaoRepository.kt`, adicionar aos imports existentes:

```kotlin
import okhttp3.OkHttpClient
```

Adicionar um novo parâmetro no construtor da classe, logo depois de `screenScraperApi`:

```kotlin
class SincronizacaoRepository(
    private val context: Context,
    private val jogoDao: JogoDao,
    private val posseUsuarioDao: PosseUsuarioDao,
    private val sincronizacaoStatusDao: SincronizacaoStatusDao,
    private val screenScraperApi: ScreenScraperApi,
    private val okHttpClient: OkHttpClient,
) {
```

Substituir o branch `is ResultadoBusca.Sucesso -> { ... }` (dentro do `for` de `sincronizarInterno`) por:

```kotlin
                is ResultadoBusca.Sucesso -> {
                    falhasRedeConsecutivas = 0
                    val jogoEntity = ScreenScraperMapper.paraJogoEntity(resultado.jeu)
                    if (jogoEntity == null) {
                        sincronizacaoStatusDao.salvar(
                            SincronizacaoStatusEntity(item.crc, StatusSincronizacao.FALHA, null, "Resposta sem id/nome válidos")
                        )
                    } else {
                        val caminhoCapa = jogoEntity.urlCapa?.let { url ->
                            CapaDownloader.baixar(context, okHttpClient, jogoEntity.id, url)
                        }
                        jogoDao.inserirTodos(listOf(jogoEntity.copy(caminhoCapaLocal = caminhoCapa)))
                        sincronizacaoStatusDao.salvar(
                            SincronizacaoStatusEntity(item.crc, StatusSincronizacao.SUCESSO, jogoEntity.id, null)
                        )
                        sucesso++
                    }
                }
```

Na `companion object`, dentro de `obterInstancia`, adicionar `okHttpClient = NetworkModule.okHttpClient` na construção:

```kotlin
                    SincronizacaoRepository(
                        context = context.applicationContext,
                        jogoDao = banco.jogoDao(),
                        posseUsuarioDao = banco.posseUsuarioDao(),
                        sincronizacaoStatusDao = banco.sincronizacaoStatusDao(),
                        screenScraperApi = NetworkModule.screenScraperApi,
                        okHttpClient = NetworkModule.okHttpClient,
                    ).also { instancia = it }
```

- [ ] **Step 4: Compilar**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/data/remote/screenscraper/NetworkModule.kt app/src/main/java/com/thalys/catalogosnes/data/sync/CapaDownloader.kt app/src/main/java/com/thalys/catalogosnes/data/sync/SincronizacaoRepository.kt
git commit -m "feat: baixa e persiste a capa como arquivo local durante a sincronização"
```

---

### Task 3: UI usa capa local com fallback pra URL remota

**Files:**
- Modify: `app/src/main/java/com/thalys/catalogosnes/data/local/JogoEntity.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaCategoriaCompleta.kt`
- Modify: `app/src/main/java/com/thalys/catalogosnes/ui/detalhe/TelaDetalheJogo.kt`

**Interfaces:**
- Consumes: `JogoEntity.caminhoCapaLocal`/`.urlCapa` (Task 1).
- Produces: `JogoEntity.modeloCapa(): Any?` — extension function, pacote `com.thalys.catalogosnes.data.local`, usada pelos 3 `AsyncImage` deste task como `model`.

Sem teste automatizado (Compose UI, ver Global Constraints); verificação visual é a Task 4.

- [ ] **Step 1: Adicionar a extensão `modeloCapa()` em `JogoEntity.kt`**

Adicionar o import `java.io.File` e, no final do arquivo (depois do `data class JogoEntity`), a função:

```kotlin
/**
 * Modelo pra passar direto ao `AsyncImage` do Coil: arquivo local se a capa já foi baixada
 * (funciona offline), senão a URL remota como melhor esforço (exige rede) — cobre jogos
 * sincronizados antes de [caminhoCapaLocal] existir, sem forçar re-sincronização completa.
 */
fun JogoEntity.modeloCapa(): Any? = caminhoCapaLocal?.let { File(it) } ?: urlCapa
```

- [ ] **Step 2: `TelaBiblioteca.kt` — `CartaoJogo`**

Trocar (dentro de `CartaoJogo`):

```kotlin
            AsyncImage(
                model = jogoComPosse.jogo.urlCapa,
```

por:

```kotlin
            AsyncImage(
                model = jogoComPosse.jogo.modeloCapa(),
```

- [ ] **Step 3: `TelaCategoriaCompleta.kt` — `CartaoJogoGrid`**

Trocar (dentro de `CartaoJogoGrid`):

```kotlin
            AsyncImage(
                model = jogoComPosse.jogo.urlCapa,
```

por:

```kotlin
            AsyncImage(
                model = jogoComPosse.jogo.modeloCapa(),
```

- [ ] **Step 4: `TelaDetalheJogo.kt`**

Trocar:

```kotlin
                AsyncImage(
                    model = estado.jogo?.urlCapa,
```

por:

```kotlin
                AsyncImage(
                    model = estado.jogo?.modeloCapa(),
```

- [ ] **Step 5: Compilar**

Run:
```bash
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/thalys/catalogosnes/data/local/JogoEntity.kt app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaBiblioteca.kt app/src/main/java/com/thalys/catalogosnes/ui/biblioteca/TelaCategoriaCompleta.kt app/src/main/java/com/thalys/catalogosnes/ui/detalhe/TelaDetalheJogo.kt
git commit -m "feat: UI usa capa local (offline) com fallback pra URL remota"
```

---

### Task 4: Verificação manual no S25 — capas sobrevivem sem internet

**Files:** nenhum (só verificação — device físico já usado nas features anteriores, serial `RQCY70208AF`).

**Interfaces:**
- Consumes: app completo (Tasks 1-3).
- Produces: evidência visual (screenshots) confirmando que a capa aparece mesmo com o aparelho offline; nenhuma interface de código.

**Atenção:** o Step 2 abaixo apaga todos os dados locais do app no device (`pm clear`) pra forçar uma sincronização do zero — é a única forma de testar o caminho novo de download, já que os 5 jogos já sincronizados antes desta mudança ficam marcados `SUCESSO` no checkpoint e não seriam reprocessados numa sincronização normal. Como hoje só existem esses 5 jogos de teste (sem posse marcada), isso é seguro; se no futuro isso rodar com dados reais do usuário, `pm clear` não seria apropriado.

- [ ] **Step 1: Build e instalar**

```bash
cd /home/thalys/Projetos/Pessoal/catalogo-games-snes
export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2
./gradlew :app:assembleDebug
adb -s RQCY70208AF install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: `BUILD SUCCESSFUL`, `Success` no install.

- [ ] **Step 2: Limpar dados do app pra forçar sincronização do zero**

```bash
adb -s RQCY70208AF shell pm clear com.thalys.catalogosnes
adb -s RQCY70208AF shell am start -n com.thalys.catalogosnes/.MainActivity
```
Expected: app abre com a biblioteca vazia (banco recriado do zero, seed de 25 jogos populada via `onCreate`).

- [ ] **Step 3: Rodar a sincronização por tempo suficiente pra alguns jogos completarem**

Achar o ícone de sincronizar via `adb shell uiautomator dump` (`content-desc="Sincronizar catálogo"`), tocar; na tela seguinte, tocar em "Iniciar sincronização". Esperar uns 20-30 segundos (throttle de 1,2s por item + tempo de rede cobre uns 10+ itens nesse intervalo), depois voltar pra biblioteca (não precisa esperar terminar).

- [ ] **Step 4: Confirmar no banco que `caminhoCapaLocal` foi preenchido**

```bash
adb -s RQCY70208AF exec-out run-as com.thalys.catalogosnes cat databases/catalogo_snes.db > /tmp/claude-1000/-home-thalys-Projetos-Pessoal-catalogo-games-snes/41c8e63f-e8fc-4981-9907-3ca1302e4b0a/scratchpad/verificacao_capas.db
nix-shell -p sqlite --run "sqlite3 /tmp/claude-1000/-home-thalys-Projetos-Pessoal-catalogo-games-snes/41c8e63f-e8fc-4981-9907-3ca1302e4b0a/scratchpad/verificacao_capas.db \"SELECT id, nome, caminhoCapaLocal FROM jogos LIMIT 5;\""
```
Expected: pelo menos algumas linhas com `caminhoCapaLocal` não-nulo, apontando pra um caminho tipo `/data/user/0/com.thalys.catalogosnes/files/capas/<id>.jpg`.

(Nota: se o `.db` sozinho vier vazio, o banco está em modo WAL — repetir puxando também `-wal`/`-shm` juntos, como nas investigações anteriores desta sessão.)

- [ ] **Step 5: Desligar internet e reabrir o app**

```bash
adb -s RQCY70208AF shell svc wifi disable
adb -s RQCY70208AF shell svc data disable
adb -s RQCY70208AF shell am force-stop com.thalys.catalogosnes
adb -s RQCY70208AF shell am start -n com.thalys.catalogosnes/.MainActivity
sleep 3
adb -s RQCY70208AF shell screencap -p /sdcard/capas_offline.png
adb -s RQCY70208AF pull /sdcard/capas_offline.png /tmp/claude-1000/-home-thalys-Projetos-Pessoal-catalogo-games-snes/41c8e63f-e8fc-4981-9907-3ca1302e4b0a/scratchpad/capas_offline_s25.png
adb -s RQCY70208AF shell rm /sdcard/capas_offline.png
```
Ler o screenshot e confirmar: as capas dos jogos já sincronizados aparecem normalmente, mesmo sem internet — isso é o teste que falhava antes desta mudança (reproduzindo o teste que o usuário fez manualmente).

- [ ] **Step 6: Checar logcat por crash em todo o fluxo**

```bash
adb -s RQCY70208AF logcat -d -t 500 | grep -iE "AndroidRuntime|FATAL|catalogosnes.*Exception" || echo "sem erros"
```
Expected: `sem erros` (ou nenhuma linha relevante).

- [ ] **Step 7: Religar internet**

```bash
adb -s RQCY70208AF shell svc wifi enable
adb -s RQCY70208AF shell svc data enable
```

---

## Self-Review

**Cobertura da spec:** schema novo (`caminhoCapaLocal`, migração real) → Task 1; download + persistência em arquivo local durante a sincronização, com fallback best-effort → Task 2; UI usa arquivo local com fallback pra URL remota (cobre jogos pré-migração) → Task 3; teste real de "funciona offline" replicando o teste que o usuário fez manualmente (wifi+dados desligados, capas ainda aparecem) → Task 4.

**Placeholders:** nenhum "TBD"/"implementar depois" — todo código é literal, pronto pra colar.

**Consistência de tipos:** `CapaDownloader.baixar(context: Context, okHttpClient: OkHttpClient, jogoId: Long, url: String): String?` (Task 2) chamado com esses mesmos tipos em `SincronizacaoRepository` (Task 2, Step 3). `JogoEntity.modeloCapa(): Any?` (Task 3, Step 1) usado idêntico nos 3 `AsyncImage` (Task 3, Steps 2-4). `JogoEntity.caminhoCapaLocal: String?` (Task 1) usado com esse nome exato em `CapaDownloader`/`SincronizacaoRepository` (Task 2) e em `modeloCapa()` (Task 3).
