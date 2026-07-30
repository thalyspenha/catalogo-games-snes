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

**Sync em batch do catálogo — as 12 tasks de implementação estão concluídas (2026-07-30).** Spec em `docs/superpowers/specs/2026-07-30-sync-batch-screenscraper-design.md`, plano em `docs/superpowers/plans/2026-07-30-sync-batch-screenscraper.md`, executado via subagent-driven-development num worktree isolado (`.claude/worktrees/sync-batch-screenscraper`, branch `worktree-sync-batch-screenscraper`), ledger em `.superpowers/sdd/2026-07-30-sync-batch-screenscraper/progress.md` dentro do worktree.

Resultado: catálogo mestre real gerado (módulo `:ferramentas` pré-processa o DAT No-Intro → `app/src/main/assets/snes_catalogo_mestre.json`, **1763 jogos únicos**), `SincronizacaoRepository` novo (orquestra throttle/retry/cota/cancelamento contra a API real do ScreenScraper, com checkpoint em Room pra retomar sync interrompido), `TelaSincronizacao` acessível pelo ícone de refresh na `TelaBiblioteca`. `AppDatabase` subiu pra version 2. Projeto ganhou infra de teste JUnit (não tinha nenhuma antes). A Task 9 (`SincronizacaoRepository`) precisou de 1 rodada de fix (revisão em Opus achou 7 Importantes) e ganhou um estado `SincronizacaoEstado.Erro` não previsto no plano original.

**Falta:** a etapa final do skill (revisão de todo o branch + merge via `finishing-a-development-branch`) — deliberadamente adiada pra outra sessão por orçamento de contexto. Nenhuma das 12 tasks precisa ser redespachada.

Falta (fora do sync):
- Carrosséis por categoria (gênero, ano, "meus jogos", "faltam") — hoje a biblioteca é um grid único.
- Captura/seleção de foto da própria cópia do jogo (campo já existe no modelo, falta a UI de câmera/picker).
- Filtros e busca na biblioteca.
