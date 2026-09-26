# Histórico do projeto

Condensado das seções "Status atual" do CLAUDE.md (estado de 2026-08-03), preservado antes do enxugamento daquele arquivo. O device de teste é um Samsung Galaxy S25 físico, serial `RQCY70208AF`.

## Credenciais ScreenScraper (2026-07-30)
- `devid` e `devpassword` ficam em `local.properties` (fora do git). Foram validados via curl em `systemesListe.php` e `jeuInfos.php`.
- **Atenção:** no painel do ScreenScraper, "Usuário Dev" é o `devid` e "Senha" é o `devpassword`. "Depurar senha" é outra coisa (modo debug da API, não usado). Essa confusão já aconteceu uma vez.

## UI funcional com seed (2026-07-30)
- O fluxo Biblioteca → detalhe → edição de posse funciona de ponta a ponta sobre o seed local (25 jogos).

## Sync em batch (2026-07-30), concluído
- Branch `worktree-sync-batch-screenscraper`, 12 tasks via SDD. Spec e plano em `docs/superpowers/*/2026-07-30-sync-batch-screenscraper*`.
- Entregas: catálogo mestre (1763 jogos), `SincronizacaoRepository` (throttle/retry/cota/cancelamento/checkpoint), `TelaSincronizacao` (ícone de refresh), AppDatabase v2 e infraestrutura JUnit.
- A Task 9 precisou de um fix round (7 achados Important na revisão Opus) e ganhou o estado `SincronizacaoEstado.Erro`.
- Fechamento: 17 testes verdes, merge fast-forward, push, branch deletado. `main` ficou em `81c18e3`.

## Carrosséis por categoria (2026-07-30), depois removidos (2026-08-03)
- Branch `worktree-carrosseis-biblioteca`, 3 tasks. Criou `MontadorCarrosseisBiblioteca.kt` com as linhas Meus jogos/Faltam/Gêneros/Anos, "Sem gênero"/"Sem ano" e `LazyColumn` + `LazyRow`.
- Minor parked: agrupamento na main thread, `LinhaCarrossel` não-`@Immutable`, key collision teórica, gap de teste.

## Índice de navegação e "Ver tudo" (2026-07-30), depois removidos (2026-08-03)
- Branch `worktree-indice-navegacao-biblioteca`, 4 tasks. Criou `TipoCategoria`, corte em 20 jogos por linha, `TelaCategoriaCompleta` (grid de 3 colunas) e barra de chips. Chegou a 27 testes.
- Fix wave com 4 Important: crash com gênero vazio (rota `categoria/` inválida), `animateScrollToItem` trocado por `scrollToItem`, chips desabilitados quando a categoria não existe, e estados carregando/vazio/inexistente. 10 Minor parked.

## Verificação no S25 (2026-07-31)
- Build debug rodou no S25 a partir do NixOS, sem crash nem ANR. Capas em placeholder (sync de imagens ainda não tinha rodado). O agrupamento de ~1700 jogos não causou ANR.

## Filtros e busca (2026-07-31), concluído
- Plano `2026-07-31-filtros-busca-biblioteca.md`, 5 tasks. Criou `filtrarPorNome`, extraiu `GridDeJogos` e adicionou `resultadoBusca`/`consultaBusca` no ViewModel e uma lupa na TopAppBar. Busca "Ninjas" encontrou "3 Ninjas Kick Back" no S25.
- Revisão final (Opus), com fixes em `606dd21`: `buscaExpandida` virou `rememberSaveable`, `BackHandler` fecha só a busca, `trim()` na consulta, `FocusRequester` e `clearFocus`.
- 4 Minor corrigidos depois em `7e17559`: `montarCarrosseis` não recomputa mais a cada tecla, `consultaBusca` virou um StateFlow separado, padding do `GridDeJogos` unificado e early-return morto removido.

## Ícone do app (2026-07-31)
- Ícone adaptativo com os botões X azul, Y verde, A vermelho e B amarelo (esquema SFC/PAL) sobre um encaixe em trevo `#1C1926` e fundo `SnesRoxo #2B1B4C`. Passou por 5 iterações com `rsvg-convert`; o conceito de cartucho foi descartado.
- Commit `4dbf7ac`. Confirmado no S25 com a máscara circular do One UI.

## Capas locais offline (2026-07-31), concluído
- Bug: as capas sumiam offline. Causa: `Cache-Control: no-cache, must-revalidate` do ScreenScraper, que impede o Coil de reusar o cache sem rede.
- Tasks: `caminhoCapaLocal` + migração 2→3 (`95815db`), `CapaDownloader` + integração no sync (`1e8fd3d`), `modeloCapa()` nos 3 `AsyncImage` (`ed3904c`). Verificado no S25 offline.
- Revisão final (Opus): 3 Important (tamanho de ~880 MB, Auto Backup, download não cancelável). Round 1 em `974535e` (maxwidth + `noBackupFilesDir`; a tentativa com `invokeOnCompletion` falhou). Round 2 em `90a3b23` (`enqueue` + `suspendCancellableCoroutine`).
- Reverificação no S25: `noBackupFilesDir` ok e cancelamento ok, mas o `maxwidth=600` não reduziu o tamanho (média ~516 KB, até 1,35 MB) e cortava capas. Revertido em `2d87fd4`.
- **Minor parked (pendentes):** `CapaDownloader` engole `CancellationException`; PNG salvo como `.jpg`; o download de capa dobra as requisições (~3526); falha de download silenciosa, sem log; sem limpeza de capas órfãs; jogos sincronizados antes da feature não ganham capa local.

## Aviso: contador de requisições (2026-07-31)
- O contador do ScreenScraper subia sem o app estar em uso. Não se achou atividade de rede no S25, e a causa não foi identificada. Suspeita: credenciais usadas em outro ambiente (Mac, emulador, script). Pode estar relacionado às chamadas extras de download de capa.

## Filtro "Quero ter" (2026-08-03), concluído
- Branch `worktree-filtro-quero-ter-biblioteca`. Commits `fed4208` (enum + filtro), `020814a` (chip), `cfd12b1` (CLAUDE.md) e `adfe746` (KDocs + testes). Merge em `d3747cc`.
- A duplicação de jogos QUERO_TER em "Faltam" é proposital. Verificado no S25.

## Grid único + menu lateral (2026-08-03), concluído
- Branch `worktree-worktree-grid-home-filtro-lateral`. Task 1: `FiltroBiblioteca.kt` com 12 testes. Task 2: grid de 4 colunas, drawer e remoção dos carrosséis, "Ver tudo" e rota `categoria/{titulo}`. Fix de scroll no drawer. Task 3: verificação no S25.
- Revisão final: fix em `5199eb9`, que inclui `key(filtroSelecionado)` para resetar o scroll, commit do plano, `generosDisponiveis`/`anosDisponiveis` fora do `combine`, remoção do `BackHandler` morto e `submenuExpandido` com `rememberSaveable`. Merge em `3676d0f`.
- **Minor pendentes:** o item pai Gênero/Ano não fica marcado como ativo; a mensagem de vazio é genérica (filtro vs busca); `GridDeJogos` poderia ser `private`; lacunas de teste; `Column` não-lazy no drawer.

## ContentScale.Fit (2026-08-03), concluído
- Commit `7eac28d`, documentado em `aac35b3`. Verificado no S25.

## Posses do seed preservadas na primeira sync (2026-09-26)
- Achado Crítico da auditoria de 2026-09-26: a primeira sync apagava `posse_usuario`. Agora as posses vão para `filesDir/posses_pendentes_sync.json` e são reassociadas pelo nome normalizado (`data/sync/PossesPendentes.kt`). Limpeza passou a apagar posse antes de jogos (FK). 3 testes novos (`PossesPendentesTest`). Não verificado no S25 (aparelho desconectado).

## Upgrade de toolchain (2026-09-26)
- Motivo: o JBR do Android Studio no Mac virou Java 25, que o Gradle 8.9 não roda.
- Gradle 8.9 → 9.8.0, AGP 8.7.2 → 9.4.1 (Kotlin embutido, plugin `kotlin.android` removido, `kotlinOptions` → `kotlin { compilerOptions }`), Kotlin 2.0.21 → 2.4.20, KSP → 2.3.12, Room 2.6.1 → 2.8.5, Compose BOM → 2026.09.00, compileSdk 35 → 37 (exigido pelas libs novas; targetSdk continua 35), core-ktx 1.19.1, lifecycle 2.11.0, activity-compose 1.13.0, navigation 2.10.2, retrofit 2.12.0, kotlinx.serialization 1.11.0. `material-icons-core` virou dependência explícita.
- Fora do upgrade de propósito (majors com API diferente): Coil 3, Retrofit 3, OkHttp 5.
- `./gradlew :app:testDebugUnitTest :ferramentas:test :app:assembleDebug` verde (28 testes). Não verificado no S25 (aparelho desconectado). Não testado no NixOS.

## Pendências de produto
- Captura e seleção da foto da própria cópia (o campo `caminhoFoto` existe, mas não há UI).
