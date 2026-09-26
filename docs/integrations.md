# Integrações

## ScreenScraper.fr

Detalhes de endpoints/DTOs/mapper em `docs/api.md`.

### Autenticação com a API

Todas as chamadas enviam credenciais como **query parameters** (não há header de auth nem interceptor):

| Query | Origem | Enviado? |
|---|---|---|
| `devid` | `BuildConfig.SCREENSCRAPER_DEVID` | sempre |
| `devpassword` | `BuildConfig.SCREENSCRAPER_DEVPASSWORD` | sempre |
| `softname` | `BuildConfig.SCREENSCRAPER_SOFTNAME` | sempre |
| `ssid` | `BuildConfig.SCREENSCRAPER_USER_ID` | só se não estiver em branco (`ifBlank { null }`) |
| `sspassword` | `BuildConfig.SCREENSCRAPER_USER_PASSWORD` | só se não estiver em branco |

- `app/build.gradle.kts` lê `local.properties` (fora do git) e gera os `buildConfigField` com as chaves: `SCREENSCRAPER_DEVID`, `SCREENSCRAPER_DEVPASSWORD`, `SCREENSCRAPER_SOFTNAME`, `SCREENSCRAPER_USER_ID`, `SCREENSCRAPER_USER_PASSWORD`. Chave ausente vira string vazia.
- Wrapper: `data/remote/screenscraper/ScreenScraperCredenciais.kt`. `credenciaisDeDesenvolvedorConfiguradas` = `devId` e `devPassword` não vazios.
- Sem credenciais de desenvolvedor, `sincronizar()` emite `SincronizacaoEstado.Erro("Credenciais do ScreenScraper não configuradas ...")` e não chama a API.
- As credenciais ficam embutidas no APK (BuildConfig).

### Sincronização em batch — `SincronizacaoRepository`

Arquivo: `data/sync/SincronizacaoRepository.kt`. Disparada manualmente pela UI (`ui/sincronizacao/SincronizacaoViewModel.kt` chama `repository.sincronizar()`); singleton via `obterInstancia(context)`.

Constantes (exatas):

| Constante | Valor |
|---|---|
| `THROTTLE_MS` | `1200L` (delay antes de cada item) |
| `MAX_TENTATIVAS_REDE` | `3` |
| `BACKOFF_MS` | `[2000L, 4000L]` (entre tentativas 1→2 e 2→3) |
| `LIMIAR_FALHAS_REDE_CONSECUTIVAS` | `5` |
| `CODIGOS_HTTP_COTA` | `{429, 430, 431}` |

Fluxo:
1. Trava de reentrância: se estado atual é `EmAndamento`, retorna (checagem não atômica).
2. Valida credenciais (ver acima).
3. Marca `EmAndamento(0, 0, "")` e roda `sincronizarInterno()` em `Dispatchers.IO`. No `finally`, se ainda estiver `EmAndamento` (ex.: cancelamento), volta para `Ocioso`.
4. Carrega `assets/snes_catalogo_mestre.json` (1763 itens).
5. Se `sincronizacao_status` estiver vazia (primeira sync): a sync salva as posses existentes em `filesDir/posses_pendentes_sync.json` (`PossesPendentesStore`, junto com pendências antigas), depois executa `posseUsuarioDao.limparTudo()` e `jogoDao.limparTudo()` (posse primeiro, por causa da FK). A cada jogo inserido pela sync, a posse pendente de mesmo nome normalizado (`normalizarNomeParaCasamento`: minúsculas, sem acento, sem pontuação, sem "the") é regravada com o id novo e sai do arquivo. Pendências sem casamento ficam no arquivo e são tentadas de novo nas syncs seguintes (`data/sync/PossesPendentes.kt`).
6. Checkpoint: lê CRCs com `SUCESSO`; `calcularRestante()` (`data/sync/CalculoRestante.kt`) filtra os itens sem sucesso (pendentes + falhas voltam à fila).
7. Para cada item restante: `ensureActive()`, `delay(1200)`, atualiza progresso, `buscarComRetry(item)` (`jeuInfos.php` com `systemeid=4`, `romnom`, `romtaille`, `crc`).

Resultados por item:

| Resultado | Ação |
|---|---|
| Sucesso, mapper ok | Baixa capa (best-effort), `inserirTodos` com `caminhoCapaLocal`, grava `SUCESSO` com `jogoId`; zera contador de falhas de rede |
| Sucesso, mapper `null` | Grava `FALHA` "Resposta sem id/nome válidos"; zera contador de falhas de rede |
| `response.jeu` nulo | Grava `FALHA` "Jogo não encontrado no ScreenScraper"; zera contador |
| Cota esgotada | Estado `CotaEsgotada(sucesso, restantes = total - sucesso)` e para |
| Erro de rede (após 3 tentativas) | Grava `FALHA` com mensagem; incrementa contador; com 5 seguidas → `CotaEsgotada` e para (disjuntor) |

Ao final: `Concluido(sucesso, falhas)` com a lista de `FALHA` do banco.

Retry (`buscarComRetry`):
- `CancellationException` é relançada (nunca engolida).
- `HttpException` com código 429/430/431 → `CotaEsgotada` imediato; outros códigos → retry com backoff.
- Qualquer outra `Exception` → retry com backoff.
- Detecção de cota também por `cotaEsgotada(resposta)`: `header.error` (lowercase) contendo `"quota"` ou `"limite"` (heurística, texto real não observado segundo o comentário).

Cancelamento: cooperativo via `ensureActive()`/`delay`; o escopo é o da coroutine do ViewModel (`SincronizacaoViewModel`). Não há WorkManager: a sync não continua se o processo/tela for encerrado, mas é retomável pelo checkpoint.

Estados (`data/sync/SincronizacaoEstado.kt`): `Ocioso`, `EmAndamento(atual, total, nomeJogoAtual)`, `Concluido(sucesso, falhas)`, `CotaEsgotada(sucesso, restantes)`, `Erro(mensagem)`.

### `CapaDownloader`

Arquivo: `data/sync/CapaDownloader.kt`.
- Motivo: o ScreenScraper serve capas com `Cache-Control: no-cache, must-revalidate`, então o cache do Coil sozinho não funciona offline.
- Usa o mesmo `NetworkModule.okHttpClient` (timeouts 30 s), `GET` na `urlCapa`, via `enqueue` dentro de `suspendCancellableCoroutine`; cancelar a coroutine chama `Call.cancel()`.
- Salva em `noBackupFilesDir/capas/<jogoId>.jpg`, retorna caminho absoluto.
- Qualquer falha (HTTP não-2xx, corpo nulo, exceção — inclusive `CancellationException`, pois o `catch (e: Exception)` é genérico) → retorna `null`, sem log. O jogo é salvo mesmo sem capa local.
- Cada download conta como uma requisição adicional ao servidor do ScreenScraper; se isso consome cota da API: Não identificado.

## Coil

- Dependência `io.coil-kt:coil-compose` 2.7.0 (`gradle/libs.versions.toml`).
- Nenhum `ImageLoader`/`ImageLoaderFactory`/config de `diskCache`/`memoryCache` customizado encontrado: usa o `ImageLoader` padrão do Coil.
- Modelo passado ao `AsyncImage`: `JogoEntity.modeloCapa()` → `File(caminhoCapaLocal)` se o campo não for nulo (sem checar existência do arquivo), senão `urlCapa`.

## DAT No-Intro (módulo `:ferramentas`)

- Entrada: `Nintendo - Super Nintendo Entertainment System.dat` (arquivo do usuário, fora do repo). Ferramenta JVM offline, não empacotada no APK.
- `ferramentas/src/main/kotlin/ferramentas/GerarCatalogoMestreSnes.kt`:
  - `parsearDat()`: XML via `javax.xml.parsers.DocumentBuilderFactory`; de cada `<game>` lê `id`, `cloneofid`, `name`, `<category>`s e o **primeiro** `<rom>` (`name`, `crc`, `size`); ignora jogos sem `<rom>` ou com `size` inválido.
  - `agruparEDeduplicar()`: mantém só jogos cujas categorias são exatamente `{"Games"}`; agrupa por `cloneofid ?: id`; representante = raiz (sem `cloneofid`) ou, na falta dela, o de menor `id`; ordena por `nomeExibicao`.
  - Saída: JSON `prettyPrint` de `ItemCatalogoMestre` (`romNome`, `crc`, `romTamanho`, `nomeExibicao`) → `app/src/main/assets/snes_catalogo_mestre.json` (1763 itens).
- Execução: `./gradlew :ferramentas:run --args="'<caminho do .dat>' '<caminho de saída .json>'"`.

## Autenticação / autorização do app

Não há. Nenhum login, conta de usuário, sessão ou controle de permissões no app. As únicas credenciais são as do ScreenScraper (build-time, ver acima). Permissões no Manifest: apenas `android.permission.INTERNET`.

## Filas / background workers

Não há. Sem WorkManager (nenhuma dependência nem uso), sem `Service`, `BroadcastReceiver` ou `JobScheduler` no `AndroidManifest.xml` (só a `MainActivity`). Trabalho assíncrono é feito com coroutines:
- Seed do banco: `CoroutineScope(Dispatchers.IO)` no callback `onCreate` do Room.
- Sincronização: coroutine do `SincronizacaoViewModel`, com `withContext(Dispatchers.IO)`.
