# Catálogo de Jogos SNES

App Android para catalogar jogos de Super Nintendo (SNES), feito para o hobby do usuário com games retro/repro. Uso pessoal, alvo de teste principal: Samsung Galaxy S25.

## Idioma

Toda comunicação sobre este projeto deve ser em português do Brasil (pt-br).

## Forma de trabalho

Sempre usar multiagentes especialistas (Agent tool, com `subagent_type` apropriado) para conduzir o trabalho neste projeto, em vez de fazer tudo inline na conversa principal. Pedido explícito do usuário (2026-07-29). Vale para qualquer ambiente (Mac ou Linux).

## Escopo funcional

- Biblioteca completa dos jogos de SNES (todos os lançados, não só os do usuário), com capa e descrição de cada jogo.
- UI estilo Netflix: grid/carrosséis navegáveis por categoria (gênero, ano, "meus jogos", "faltam").
- Usuário marca quais jogos já possui, com status desde o início (não é um check simples):
  - Tenho / Quero ter / Não me interessa
  - Completude (CIB: cartucho, caixa, manual)
  - Foto da própria cópia
  - Nota de condição
- Dados salvos localmente (Room/SQLite) para uso offline (ex: em lojas/feiras de repro sem sinal).

## Fonte de dados

**ScreenScraper.fr** — escolhida por ser a referência da comunidade retro/repro, com boa cobertura de SNES e capas em várias versões regionais (US/EU/JP).

Estratégia: baixar os dados uma vez (batch) e persistir localmente, em vez de bater na API a cada uso — evita depender de internet no dia a dia do app.

## Stack (proposta, a confirmar durante a implementação)

- Kotlin + Jetpack Compose
- Room (persistência local)
- Retrofit (chamadas à API do ScreenScraper)
- Coil (carregamento/cache de imagens de capa)

## Ambientes de desenvolvimento

O projeto é desenvolvido em dois ambientes:
- macOS (M1) — este Mac
- Linux — ambiente principal, com Android Studio completo, rodando **NixOS**

Por isso o contexto do projeto fica registrado aqui neste `CLAUDE.md` (versionado no git), em vez de depender de memória local do Claude Code, que não é compartilhada entre máquinas.

No ambiente Linux (NixOS), `java`/`python3`/`node`/`kotlinc` não ficam no PATH direto, mas são alcançáveis (`nix-shell -p <pacote> --run "<comando>"`, ou apontando pro caminho em `/nix/store`). Toda invocação de `./gradlew` precisa de `JAVA_HOME` apontado manualmente pra um JDK do `/nix/store` (ex: `export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2` — se esse caminho não existir mais, `find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d` acha o atual).

No Mac (2026-07-30), `java`/`JAVA_HOME` também podem faltar — nesta máquina o Homebrew `openjdk@17` ficou com symlink quebrado em `/Library/Java/JavaVirtualMachines/openjdk-17.jdk`. Workaround que funcionou: usar o JBR (JetBrains Runtime) embutido no Android Studio como `JAVA_HOME`: `/Applications/Android Studio.app/Contents/jbr/Contents/Home` (OpenJDK 21).

## Estrutura do projeto

- `app/build.gradle.kts` — módulo Android, Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 26
- `gradle/libs.versions.toml` — catálogo de versões (Compose BOM 2024.10.01, Room 2.6.1, Retrofit 2.11.0, Coil 2.7.0)
- `app/src/main/java/com/thalys/catalogosnes/`
  - `MainActivity.kt` — hospeda só o `CatalogoNavHost` (grid hardcoded antigo foi removido)
  - `ui/theme/` — tema Compose (paleta escura estilo "Netflix")
  - `ui/biblioteca/` — `TelaBiblioteca` (grid único via `LazyVerticalGrid`, capa via Coil, selo de status) + `BibliotecaViewModel` (observa `JogoRepository.observarBiblioteca()`)
  - `ui/detalhe/` — `TelaDetalheJogo` (metadados do jogo + edição de posse: seletor Tenho/Quero ter/Não interessa, checkboxes CIB, campo de nota; foto da cópia é placeholder/TODO, sem picker real ainda) + `DetalheJogoViewModel`
  - `ui/navigation/CatalogoNavHost.kt` — rotas `"biblioteca"` e `"detalhe/{jogoId}"` (Navigation Compose)
  - `data/local/` — Room: `JogoEntity` (metadados do catálogo), `PosseUsuarioEntity` (status pessoal: TENHO/QUERO_TER/NAO_INTERESSA, completude CIB, foto, nota de condição), `JogoComPosse` (relação), DAOs, `AppDatabase` (com `obterInstancia(context)`, popula seed via `RoomDatabase.Callback.onCreate`)
  - `data/local/seed/` — `JogoSeedDto.kt` + `SeedLoader.kt`: parseiam `app/src/main/assets/jogos_seed.json` (25 jogos reais de SNES, com `id` fixo) para `List<JogoEntity>`
  - `data/model/StatusPosse.kt` — enum de status de posse
  - `data/repository/JogoRepository.kt` — expõe `observarBiblioteca()`, `buscarJogo(id)`, `salvarPosse(posse)`, `removerPosse(id)`; não referencia o ScreenScraper (troca de fonte futura não deve exigir mudança em Room/UI/Navigation)
  - `data/remote/screenscraper/` — camada de rede (Retrofit) para a API v2 do ScreenScraper, esqueleto pronto, ainda não usada pela UI (isolada de propósito):
    - `ScreenScraperApi.kt` — interface Retrofit (`systemesListe.php`, `jeuInfos.php`, `jeuRecherche.php`); ID do sistema SNES confirmado = 4
    - `dto/ScreenScraperDtos.kt` — DTOs kotlinx.serialization fiéis ao JSON da API (campos majoritariamente `String?`, já que a API devolve quase tudo como string); shape validado com JSON real em 2026-07-30 (`systemesListe.php`, `jeuInfos.php`), incluindo `joueurs`/`note` (`TextoSimplesDto`) e `SistemaDto.noms` (`NomesSistemaDto`, objeto de chaves fixas, não lista region/text)
    - `NetworkModule.kt` — monta OkHttpClient (com logging interceptor) + Retrofit, sem DI framework; `Json { isLenient = true }` porque `systemesListe.php` devolve `id` do sistema como número sem aspas, diferente do resto da API (que usa string)
    - `ScreenScraperCredenciais.kt` — wrapper das credenciais lidas do `BuildConfig`
    - `ScreenScraperMapper.kt` — converte `JeuDto` em `JogoEntity` (prioridades de região/idioma são decisão de produto, ajustável)
- `ferramentas/` — módulo Gradle separado (Kotlin JVM puro, plugin `id("org.jetbrains.kotlin.jvm")` direto em vez de `alias()` por conflito de classpath com o `kotlin.android` do `:app`), sem dependência do Android, não empacotado no APK. Pré-processa o DAT No-Intro de SNES (`Nintendo - Super Nintendo Entertainment System.dat`, arquivo do usuário fora do repo) num catálogo mestre enxuto. `GerarCatalogoMestreSnes.kt` — `parsearDat()` (XML via `javax.xml.parsers`) + `agruparEDeduplicar()` (filtra categoria == exatamente `{Games}`, agrupa por `cloneofid`, escolhe representante); `ItemCatalogoMestre.kt` — DTO de saída (`romNome`/`crc`/`romTamanho`/`nomeExibicao`). Testado com JUnit (`ferramentas/src/test/kotlin/ferramentas/AgrupamentoClonesTest.kt`). Rodar com `./gradlew :ferramentas:run --args="'<caminho do .dat>' '<caminho de saída .json>'"`.

Pacote base: `com.thalys.catalogosnes`. Build verificado com `./gradlew assembleDebug` (sucesso). Sem DI framework (Hilt/Dagger/Koin) em lugar nenhum — padrão manual `companion object.obterInstancia(context)` reaproveitado em `NetworkModule`, `AppDatabase`, `JogoRepository` e nas Factories dos ViewModels. Testes: projeto ganhou infraestrutura de teste (JUnit 4.13.2, `libs.junit`) em 2026-07-30 — antes disso não tinha nenhum teste automatizado. Cobertura é só de lógica pura (parsing/agrupamento/cálculo); nada de Room/Android instrumentado ainda.

## Status atual

UI funcional consumindo dados reais do Room via seed local (2026-07-30). Biblioteca → detalhe → edição de posse funcionando fim a fim.

Credenciais reais do ScreenScraper (devid/devpassword) configuradas em `local.properties` (fora do git) e validadas em 2026-07-30 com chamadas reais via curl a `systemesListe.php` e `jeuInfos.php` — funcionando. Atenção: no painel do ScreenScraper a coluna "Usuário Dev" é o `devid` e "Senha" é o `devpassword`; a coluna "Depurar senha" é um valor à parte (debug mode da API, não usado aqui) — já rolou confusão entre os dois uma vez.

**Sync em batch do catálogo — concluído e mergeado em main (2026-07-30).** As 12 tasks de implementação foram feitas via subagent-driven-development num worktree isolado (branch `worktree-sync-batch-screenscraper`); spec em `docs/superpowers/specs/2026-07-30-sync-batch-screenscraper-design.md`, plano em `docs/superpowers/plans/2026-07-30-sync-batch-screenscraper.md`.

Resultado: catálogo mestre real gerado (módulo `:ferramentas` pré-processa o DAT No-Intro → `app/src/main/assets/snes_catalogo_mestre.json`, **1763 jogos únicos**), `SincronizacaoRepository` novo (orquestra throttle/retry/cota/cancelamento contra a API real do ScreenScraper, com checkpoint em Room pra retomar sync interrompido), `TelaSincronizacao` acessível pelo ícone de refresh na `TelaBiblioteca`. `AppDatabase` subiu pra version 2. Projeto ganhou infra de teste JUnit (não tinha nenhuma antes). A Task 9 (`SincronizacaoRepository`) precisou de 1 rodada de fix (revisão em Opus achou 7 Importantes) e ganhou um estado `SincronizacaoEstado.Erro` não previsto no plano original.

Fechamento do branch (2026-07-30) via `finishing-a-development-branch`: 17 testes verdes (`:app` + `:ferramentas`), merge fast-forward pra `main` sem conflito, push feito pro remoto, branch de feature deletado (local e remoto). `main` local e remota agora sincronizadas em `81c18e3`.

**Carrosséis por categoria na biblioteca — concluído (2026-07-30).** As 3 tasks do plano foram feitas via subagent-driven-development num worktree isolado (branch `worktree-carrosseis-biblioteca`); spec em `docs/superpowers/specs/2026-07-30-carrosseis-biblioteca-design.md`, plano em `docs/superpowers/plans/2026-07-30-carrosseis-biblioteca.md`.

Resultado: `MontadorCarrosseisBiblioteca.kt` (`LinhaCarrossel` + `montarCarrosseis()`, função pura, 10 testes JUnit) agrupa a biblioteca em Meus jogos / Faltam / Gêneros (A-Z) / Anos (cronológico), com linhas "Sem gênero"/"Sem ano" pros nulos. `BibliotecaViewModel` expõe `BibliotecaUiState.linhas: List<LinhaCarrossel>`. `TelaBiblioteca.kt` trocou `LazyVerticalGrid` por `LazyColumn` de carrosséis (`LinhaCarrosselView` com `LazyRow`), navegação pro detalhe preservada. Revisão final (whole-branch, 2026-07-30) não achou nada Critical; achados Minor (perf de agrupamento na main thread, `LinhaCarrossel` não-`@Immutable` pro Compose, key collision teórica no `LazyColumn`, gap de teste pontual) ficaram parked no ledger como follow-up, não bloquearam o merge.

**Índice de navegação e "ver tudo" na biblioteca — concluído (2026-07-30).** Corrige o gap de design achado na revisão final dos carrosséis (item acima). As 4 tasks foram feitas via subagent-driven-development num worktree isolado (branch `worktree-indice-navegacao-biblioteca`); spec em `docs/superpowers/specs/2026-07-30-indice-navegacao-biblioteca-design.md`, plano em `docs/superpowers/plans/2026-07-30-indice-navegacao-biblioteca.md`.

Resultado: `LinhaCarrossel` ganhou `tipo: TipoCategoria` (MEUS_JOGOS/FALTAM/GENERO/ANO); `mostrarVerTudo()`/`jogosVisiveis()` cortam cada linha em 20 jogos, com card "Ver tudo" abrindo `TelaCategoriaCompleta` (grid de 3 colunas reaproveitado do layout pré-carrosséis); `TelaBiblioteca` ganhou barra de chips no topo (Meus jogos/Faltam pulam direto, Gênero/Ano expandem drill-down por valor). 27 testes (18 em `MontadorCarrosseisBibliotecaTest`).

Revisão final (whole-branch, 2026-07-30) achou 4 Important — corrigidos num fix wave só, com re-review confirmando todos ADDRESSED sem regressão nova: crash real de gênero vazio virando rota `categoria/` inválida (agora cai em "Sem gênero"); `animateScrollToItem` trocado por `scrollToItem` (saltava animando por dezenas de linhas); chips "Meus jogos"/"Faltam" desabilitados quando a categoria não existe (estado do dia 1 pós-sync); `TelaCategoriaCompleta` agora distingue carregando/vazio/inexistente. 10 achados Minor parked como follow-up (a maioria polish; o mais relevante — "Ver tudo" de Faltam com ~1700 itens sem busca — já é coberto pelo item "Filtros e busca na biblioteca" abaixo).

**Verificação visual no device real (2026-07-31).** Build debug instalado via adb num Samsung Galaxy S25 físico (serial `RQCY70208AF`), rodando NixOS Linux com `JAVA_HOME` apontado pro JDK 21 do `/nix/store` (ver seção "Ambientes de desenvolvimento"). `./gradlew :app:assembleDebug` + `adb install` + `am start` sem erros; logcat sem crash/ANR nos primeiros segundos. Carrosséis (Faltam, Ação e Aventura, Ação e Plataforma, chips Meus jogos/Faltam/Gênero/Ano) renderizaram corretamente na tela real. Capas aparecem como placeholder cinza — esperado, sync de imagens do ScreenScraper ainda não populou esse device. Confirma que o agrupamento de ~1700 jogos em carrosséis na main thread (achado Minor de perf da revisão dos carrosséis) não causa ANR perceptível nesse aparelho.

**Filtros e busca na biblioteca — em andamento (2026-07-31).** Plano em `docs/superpowers/plans/2026-07-31-filtros-busca-biblioteca.md`, sendo executado via subagent-driven-development (ledger em `.superpowers/sdd/2026-07-31-filtros-busca-biblioteca/progress.md`, git-ignorado). Task 1/5 concluída: `filtrarPorNome(jogos, consulta)` em `MontadorCarrosseisBiblioteca.kt` — filtro por substring no nome, case-insensitive, sem accent-folding, consulta em branco retorna a lista inteira; 3 testes novos, review de task limpo (sem findings). Task 2/5 concluída: `GridDeJogos` extraído de `TelaCategoriaCompleta.kt` como composable compartilhado (grid de 3 colunas + estado vazio embutido, `mensagemVazia` parametrizável), reaproveitado pela Task 4 (grid de resultado de busca). Task 3/5 concluída: `BibliotecaViewModel` agora expõe `BibliotecaUiState.resultadoBusca: List<JogoComPosse>?` e `.consultaBusca: String`, combinando `JogoRepository.observarBiblioteca()` com um `StateFlow<String>` de consulta via `Flow.combine`; consulta em branco mantém `resultadoBusca = null` (mostra carrosséis), não-vazia filtra via `filtrarPorNome` (mostra grid). `aoMudarConsultaBusca(texto)` novo. Task 4/5 concluída: `TelaBiblioteca` ganhou ícone de lupa na TopAppBar que expande um `TextField` (substitui o título), digitando chama `viewModel::aoMudarConsultaBusca` sem debounce; enquanto `resultadoBusca != null` mostra `GridDeJogos` (reaproveitado, mensagem "Nenhum jogo encontrado") no lugar dos carrosséis; ícone X fecha a busca e limpa a consulta. Revisão da Task 4 aprovada com 2 achados Minor parked como follow-up (teclado não fecha sozinho ao fechar a busca; estado de busca expandida não sobrevive a navegar pro detalhe e voltar — mesmo comportamento já existente do `chipExpandido`). Task 5/5 concluída (2026-07-31): verificação manual no S25 físico, todos os 5 passos ok (build/install, busca com resultado "Ninjas"→"3 Ninjas Kick Back", busca sem resultado→"Nenhum jogo encontrado", fechar busca→volta pros carrosséis, logcat sem erros).

Revisão final whole-branch (2026-07-31, Opus) — **With fixes**, não bloqueou por Critical mas achou 2 Important de integração entre tasks: (1) `buscaExpandida` é `remember` local e não sobrevive a abrir um jogo e voltar, mas `consultaBusca` vive no `BibliotecaViewModel` e sobrevive — a biblioteca volta filtrada sem nenhuma UI de busca visível pra desfazer (fix sugerido: `rememberSaveable`); (2) botão back do sistema fecha o app com a busca aberta, em vez de fechar só a busca, porque `"biblioteca"` é a `startDestination` (fix sugerido: `BackHandler`). Mais 2 Minor sugeridos pra ir junto na mesma fix wave (trim() ausente em `filtrarPorNome`; abrir busca não foca o campo/teclado) e 4 Minor parked como follow-up (perf de `montarCarrosseis` recomputando a cada tecla — anexa ao achado de perf de main thread já parked desde os carrosséis; digitar durante `carregando=true` é engolido; `GridDeJogos` com `contentPadding`/`modifier.padding` inconsistente entre as duas telas; ramo de consulta em branco de `filtrarPorNome` é código morto em produção). Fix wave aplicada (commit `606dd21`): `buscaExpandida` virou `rememberSaveable` (sobrevive a abrir um jogo e voltar); `BackHandler` fecha só a busca em vez do app; `filtrarPorNome` faz `trim()` da consulta antes do `contains` (novo teste cobrindo espaço no fim); busca ganhou `FocusRequester` (foco automático ao abrir) e `LocalFocusManager.clearFocus()` (teclado fecha ao fechar busca, reaproveitado entre o X e o `BackHandler` via uma função local `fecharBusca()` compartilhada). Re-review escopado confirmou os 4 achados ADDRESSED, sem quebra nova. Os 4 Minor remanescentes também foram corrigidos (2026-07-31, commit `7e17559`), a pedido do usuário — review desse fix batch limpo, sem nenhum finding: `montarCarrosseis` agora só recomputa quando `JogoRepository.observarBiblioteca()` re-emite (pré-mapeado antes do `combine`, não mais a cada tecla digitada); `BibliotecaViewModel` expõe `consultaBusca: StateFlow<String>` separada de `estadoUi` (o campo `consultaBusca` saiu de `BibliotecaUiState`), então o `TextField` mostra o texto digitado sem depender da primeira emissão do repositório; `GridDeJogos` em `TelaBiblioteca` passou a usar `contentPadding = paddingInterno` + `modifier = Modifier.padding(8.dp)`, igual à `TelaCategoriaCompleta`; `filtrarPorNome` perdeu o early-return morto (comportamento idêntico, já que `contains("")` é sempre `true`). **Plano fechado (2026-07-31)**, nada pendente.

Falta (fora do sync, dos carrosséis e deste índice):
- Captura/seleção de foto da própria cópia do jogo (campo já existe no modelo, falta a UI de câmera/picker).
