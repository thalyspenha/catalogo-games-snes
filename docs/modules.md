# Módulos e pacotes

## Módulos Gradle

Declarados em `settings.gradle.kts` (`rootProject.name = "CatalogoGamesSnes"`):

| Módulo | Tipo | Build file | Papel |
|---|---|---|---|
| `:app` | Android application (`com.thalys.catalogosnes`) | `app/build.gradle.kts` | O app |
| `:ferramentas` | Kotlin JVM + plugin `application` | `ferramentas/build.gradle.kts` | CLI que gera `snes_catalogo_mestre.json` a partir do DAT No-Intro. Não é dependência do `:app` (nenhum `project(":ferramentas")` no app) |

## :app — pacote `com.thalys.catalogosnes` (`app/src/main/java/com/thalys/catalogosnes/`)

### Raiz
- `MainActivity.kt` — única Activity; `enableEdgeToEdge()` e `setContent { CatalogoSnesTheme { CatalogoNavHost() } }`.

### `ui/navigation`
- `CatalogoNavHost.kt` — NavHost com rotas `biblioteca`, `detalhe/{jogoId}`, `sincronizacao`.

### `ui/biblioteca`
- `TelaBiblioteca.kt` — tela principal: TopAppBar (menu, busca, sincronizar), `ModalNavigationDrawer` de filtros (`ConteudoMenuFiltro`, submenus Gênero/Ano), `GridDeJogos` (4 colunas), `CartaoJogo`, `SeloStatus`, preview.
- `BibliotecaViewModel.kt` — `BibliotecaUiState`, combinação repositório + filtro + busca, `Factory`.
- `FiltroBiblioteca.kt` — sealed class `FiltroBiblioteca` e funções puras `filtrarBiblioteca`, `generosDisponiveis`, `anosDisponiveis`, `filtrarPorNome`.

### `ui/detalhe`
- `TelaDetalheJogo.kt` — capa, metadados, seletor de status (Tenho/Quero ter/Não interessa), toggles CIB, nota de condição, botão salvar.
- `DetalheJogoViewModel.kt` — `DetalheUiState`, carga do jogo, edição e `salvar()`, `Factory(context, jogoId)`.

### `ui/sincronizacao`
- `TelaSincronizacao.kt` — UI por estado do sync (iniciar, progresso, cancelar, tentar falhas novamente).
- `SincronizacaoViewModel.kt` — inicia/cancela o Job de sync; expõe `repository.estado`.

### `ui/theme`
- `Color.kt`, `Theme.kt` (`CatalogoSnesTheme`, esquemas claro/escuro via `isSystemInDarkTheme()`), `Type.kt` (tipografia).

### `data/model`
- `StatusPosse.kt` — `TENHO`, `QUERO_TER`, `NAO_INTERESSA`.
- `StatusSincronizacao.kt` — `SUCESSO`, `FALHA`.

### `data/local` (Room)
- `AppDatabase.kt` — `@Database` version 3, `exportSchema = false`, arquivo `catalogo_snes.db`, migrações `1→2` (cria `sincronizacao_status`) e `2→3` (`caminhoCapaLocal`), callback de seed.
- `JogoEntity.kt` — tabela `jogos`; extensão `modeloCapa()`.
- `PosseUsuarioEntity.kt` — tabela `posse_usuario` (PK `jogoId`, FK para `jogos.id`).
- `SincronizacaoStatusEntity.kt` — tabela `sincronizacao_status` (PK `crc`).
- `JogoComPosse.kt` — relação `@Embedded`/`@Relation`.
- `JogoDao.kt` — `inserirTodos` (REPLACE), `observarBibliotecaCompleta` (Flow), `buscarPorId`, `listarTodosComPosse`, `limparTudo`.
- `PosseUsuarioDao.kt` — `salvar` (REPLACE), `remover`, `limparTudo`.
- `SincronizacaoStatusDao.kt` — `salvar`, `buscarCrcsPorStatus`, `buscarFalhas`, `contarLinhas`.
- Conversores de tipo (`@TypeConverters`) para enums: Não identificado (nenhum declarado; Room trata enums nativamente).

### `data/local/seed`
- `JogoSeedDto.kt` — DTO serializável do seed.
- `SeedLoader.kt` — lê `assets/jogos_seed.json` → `List<JogoEntity>`.

### `data/repository`
- `JogoRepository.kt` — `observarBiblioteca`, `buscarJogo`, `salvarPosse`, `removerPosse` (este último sem chamador no código), singleton.

### `data/remote/screenscraper`
- `ScreenScraperApi.kt` — Retrofit: `systemesListe.php` (`listarSistemas`), `jeuInfos.php` (`buscarInfoJogo`), `jeuRecherche.php` (`buscarJogoPorNome`); `SISTEMA_SNES = 4`. Apenas `buscarInfoJogo` é chamado no código de produção.
- `dto/ScreenScraperDtos.kt` — DTOs kotlinx.serialization (header, jogo, mídias, sistemas etc.).
- `NetworkModule.kt` — base URL `https://www.screenscraper.fr/api2/`, `Json` (`ignoreUnknownKeys`, `coerceInputValues`, `isLenient`), OkHttp (logging BODY só em DEBUG, timeouts 30s), Retrofit.
- `ScreenScraperCredenciais.kt` — lê os campos do `BuildConfig`.
- `ScreenScraperMapper.kt` — `JeuDto` → `JogoEntity` com prioridade de região (`us, wor, eu, ss, jp`) e idioma (`pt, en, wor`); capa preferindo `box-2D`.

### `data/sync`
- `SincronizacaoRepository.kt` — orquestra o sync (throttle, retry, cota, disjuntor, checkpoint, download de capa).
- `SincronizacaoEstado.kt` — estados do sync + `FalhaSincronizacao`.
- `CatalogoMestreItemDto.kt` / `CatalogoMestreLoader.kt` — leitura de `assets/snes_catalogo_mestre.json`.
- `CalculoRestante.kt` — `calcularRestante()` (itens sem SUCESSO).
- `CapaDownloader.kt` — download cancelável da capa para `noBackupFilesDir/capas`.

### Testes (`app/src/test/java/com/thalys/catalogosnes/`)
- `data/sync/CalculoRestanteTest.kt`, `data/sync/CatalogoMestreLoaderTest.kt`, `data/sync/CotaEsgotadaTest.kt`, `ui/biblioteca/FiltroBibliotecaTest.kt` (JUnit 4, lógica pura). Testes instrumentados (`androidTest`): Não identificado.

### Assets (`app/src/main/assets/`)
- `jogos_seed.json` — 25 jogos com `id` fixo, inseridos na criação do banco.
- `snes_catalogo_mestre.json` — 1763 itens (`romNome`, `crc`, `romTamanho`, `nomeExibicao`), gerado por `:ferramentas`.

### Recursos (`app/src/main/res/`)
- `values/strings.xml` (`app_name = "Catálogo SNES"`), `values/themes.xml` (`Theme.CatalogoSnes`, parent `android:Theme.Material.Light.NoActionBar`), `values/colors.xml` (cores do ícone), `drawable/ic_launcher_foreground.xml`, `mipmap-anydpi-v26/ic_launcher.xml` (ícone adaptativo).

## :ferramentas (`ferramentas/src/main/kotlin/ferramentas/`)
- `GerarCatalogoMestreSnes.kt` — `JogoDat`, `parsearDat()` (XML via `javax.xml.parsers`), `agruparEDeduplicar()` (categoria exatamente `{Games}`, agrupa por `cloneofid`, representante = entrada sem `cloneOfId` ou menor id; saída ordenada por `nomeExibicao`), `main(args)` (2 argumentos: DAT de entrada, JSON de saída).
- `ItemCatalogoMestre.kt` — DTO de saída.
- Teste: `ferramentas/src/test/kotlin/ferramentas/AgrupamentoClonesTest.kt`.
