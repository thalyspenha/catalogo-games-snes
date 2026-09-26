# Decisões (ADRs curtos)

A coluna "Vale hoje?" foi conferida contra o código em `main` (2026-09-26).

## ADR-01: ScreenScraper.fr como fonte de dados
- **Contexto:** o app precisa de metadados e capas de todos os jogos de SNES, em várias regiões.
- **Decisão:** usar a API v2 do ScreenScraper, que é referência da comunidade retro/repro (sistema SNES = 4).
- **Consequência:** exige credenciais de dev em `local.properties` e está sujeito a cota. O `JogoRepository` não referencia o ScreenScraper, então trocar de fonte não afeta Room nem UI.
- **Fonte:** CLAUDE.md; `ScreenScraperApi.kt`. **Vale hoje?** Sim.

## ADR-02: sync em batch, persistido para uso offline
- **Contexto:** o app é usado em lojas e feiras sem sinal.
- **Decisão:** baixar o catálogo uma vez para o Room, com checkpoint por CRC (`sincronizacao_status`) para retomar depois.
- **Consequência:** o sync é longo, com throttle de 1,2 s por jogo e ~1763 itens. A tabela de checkpoint entrou na migração 1→2.
- **Fonte:** spec `2026-07-30-sync-batch-screenscraper-design.md`; `SincronizacaoRepository.kt`. **Vale hoje?** Sim.

## ADR-03: catálogo mestre a partir do DAT No-Intro, no módulo `:ferramentas`
- **Contexto:** é preciso saber quais jogos existem (e seus CRCs) antes de consultar a API.
- **Decisão:** um módulo Kotlin JVM separado pré-processa o DAT e gera um JSON em assets. Só a categoria exatamente `{Games}` entra, com dedup de clones. Até 2026-09 o módulo usava `id("org.jetbrains.kotlin.jvm")` direto, porque `alias()` dava conflito de classpath com o `kotlin.android`. Com o AGP 9 (Kotlin embutido, sem `kotlin.android`), passou a usar `alias(libs.plugins.kotlin.jvm)`.
- **Consequência:** o módulo não vai no APK. O DAT fica fora do repositório.
- **Fonte:** CLAUDE.md; `ferramentas/build.gradle.kts`. **Vale hoje?** Sim.

## ADR-04: sem framework de DI
- **Decisão:** singletons manuais `companion object.obterInstancia(context)` e Factories de ViewModel.
- **Motivo:** Não identificado explicitamente (subentende-se simplicidade).
- **Fonte:** CLAUDE.md; `AppDatabase`, `JogoRepository`, `SincronizacaoRepository`, `NetworkModule`. **Vale hoje?** Sim.

## ADR-05: `Json { isLenient = true }`
- **Contexto:** `systemesListe.php` devolve o `id` como número sem aspas, enquanto o resto da API usa string.
- **Decisão:** parser leniente, com DTOs majoritariamente `String?`.
- **Fonte:** `NetworkModule.kt`. **Vale hoje?** Sim.

## ADR-06: capa salva como arquivo local em vez de depender do cache do Coil
- **Contexto:** o ScreenScraper manda `Cache-Control: no-cache, must-revalidate`. Offline, o Coil não consegue revalidar a imagem e ela não aparece.
- **Decisão:** baixar a capa durante o sync e guardar o caminho em `JogoEntity.caminhoCapaLocal` (migração 2→3). A UI usa `modeloCapa()`, que cai para a URL quando não há arquivo local.
- **Consequência:** ~511 KB por capa, projetando ~880 MB para o catálogo completo. Jogos sincronizados antes disso não ganham capa local automaticamente.
- **Fonte:** CLAUDE.md; `CapaDownloader.kt`, `JogoEntity.kt`. **Vale hoje?** Sim.

## ADR-07: capas em `noBackupFilesDir`
- **Contexto:** o Auto Backup tem cota de 25 MB por app. As capas estourariam essa cota e levariam junto o backup de `posse_usuario`.
- **Decisão:** gravar em `noBackupFilesDir/capas/`.
- **Fonte:** CLAUDE.md (commit `974535e`); `CapaDownloader.kt`. **Vale hoje?** Sim.

## ADR-08: download de capa cancelável
- **Decisão:** usar OkHttp `enqueue` + `suspendCancellableCoroutine`. `invokeOnCompletion` não funcionou.
- **Fonte:** commit `90a3b23`. **Vale hoje?** Sim.

## ADR-09: revert do `maxwidth=600`
- **Contexto:** o parâmetro deveria reduzir o tamanho do arquivo. Na prática, o servidor devolve PNG truecolor, às vezes maior que o original, e com capas cortadas.
- **Decisão:** usar a URL original, sem parâmetros. Se compressão voltar a ser necessária, fazer no cliente.
- **Fonte:** commit `2d87fd4`; `ScreenScraperMapper.escolherCapa`. **Vale hoje?** Sim, não há `maxwidth` no código.

## ADR-10: `ContentScale.Fit` nas capas
- **Contexto:** o `Crop` cortava a arte, porque as proporções das capas US/EU/JP variam.
- **Decisão:** `Fit` no card e no detalhe, aceitando bordas vazias.
- **Fonte:** commit `7eac28d`. **Vale hoje?** Sim (`TelaBiblioteca.kt:333`, `TelaDetalheJogo.kt:101`).

## ADR-11: grid único na home, substituindo carrosséis
- **Contexto:** a home era feita de carrosséis por categoria, com barra de índice e tela "Ver tudo".
- **Decisão:** um grid único de 4 colunas (`spacedBy(4.dp)`), com o catálogo completo ao abrir, e cards de altura fixa (nome com `minLines`/`maxLines` = 2). Os carrosséis e o "Ver tudo" foram removidos de vez.
- **Fonte:** spec `2026-08-03-grid-home-filtro-lateral-design.md`. **Vale hoje?** Sim (`GridCells.Fixed(4)`, e não existe mais `MontadorCarrosseisBiblioteca.kt`).

## ADR-12: filtro único (tipo radio) em menu lateral
- **Decisão:** um `ModalNavigationDrawer` com um filtro por vez. Gênero e Ano abrem submenu rolável. A busca ignora o filtro.
- **Fonte:** mesma spec do ADR-11; `FiltroBiblioteca.kt`. **Vale hoje?** Sim.

## ADR-13: "Faltam" inclui "Quero ter"
- **Decisão:** um jogo QUERO_TER continua faltando na coleção, então aparece nos dois filtros.
- **Fonte:** spec `2026-08-03-filtro-quero-ter-design.md`; `filtrarBiblioteca`. **Vale hoje?** Sim.

## ADR-14: prioridade de região e idioma
- **Decisão:** região `us > wor > eu > ss > jp`; idioma `pt > en > wor`. É uma escolha de produto, marcada no código como ajustável.
- **Fonte:** `ScreenScraperMapper.kt`. **Vale hoje?** Sim.

## ADR-15: migrações Room reais, sem destrutivas
- **Decisão:** `Migration(1,2)` e `Migration(2,3)` explícitas, sem `fallbackToDestructiveMigration`, para preservar os dados do usuário.
- **Fonte:** `AppDatabase.kt`. **Vale hoje?** Sim (versão 3).
