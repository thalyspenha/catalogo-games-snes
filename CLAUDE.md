# Catálogo de Jogos SNES

App Android de uso pessoal para catalogar jogos de Super Nintendo (hobby de games retro/repro). Alvo de teste: Samsung Galaxy S25 físico (serial `RQCY70208AF`). Não será publicado na Google Play: APK instalado só no aparelho do usuário, então segurança de distribuição, minify e assinatura de release não são prioridade.

## Contexto essencial

- Biblioteca completa do SNES (~1763 jogos únicos, catálogo mestre derivado do DAT No-Intro), com capa e descrição.
- Usuário marca a posse de cada jogo: Tenho / Quero ter / Não me interessa, completude CIB (cartucho, caixa, manual), foto da cópia e nota de condição.
- **Offline-first**: tudo fica em Room/SQLite, e as capas são arquivos locais. O app precisa funcionar em lojas e feiras sem sinal.
- Fonte de dados: **ScreenScraper.fr** (API v2, SNES = sistema 4), baixada em batch pela tela de sincronização, com checkpoint para retomar.

## Stack

- Gradle 9.8.0, AGP 9.4.1 (Kotlin embutido no AGP, sem plugin `kotlin.android`), Kotlin 2.4.20, KSP 2.3.12; compileSdk 37, targetSdk 35, minSdk 26; bytecode JVM 11
- Jetpack Compose (BOM 2026.09.00, Material3 + `material-icons-core` explícito), Navigation Compose 2.10.2, Lifecycle 2.11.0
- Room 2.8.5 (banco na versão 3, com migrações reais)
- Retrofit 2.12.0 + OkHttp 4.12.0 + kotlinx.serialization 1.11.0
- Coil 2.7.0
- JUnit 4.13.2

## Arquitetura resumida

Camadas UI (Compose + ViewModel) → Repository → Room / Retrofit. A UI e o Room não conhecem o ScreenScraper, que fica isolado em `data/remote/screenscraper/`. Pacote base `com.thalys.catalogosnes`:

- `ui/biblioteca/`: home em grid de 4 colunas, drawer de filtro (Todos/Tenho/Quero ter/Faltam/Gênero/Ano), busca por nome e `FiltroBiblioteca.kt`
- `ui/detalhe/`: detalhe do jogo e edição de posse
- `ui/sincronizacao/`: tela da sync em batch
- `ui/navigation/`: `CatalogoNavHost`
- `data/local/`: entidades, DAOs, `AppDatabase` e seed
- `data/repository/`: `JogoRepository`
- `data/sync/`: `SincronizacaoRepository`, `CapaDownloader` e o loader do catálogo mestre
- `data/remote/screenscraper/`: API, DTOs, mapper e `NetworkModule`
- `data/model/`: enums de status
- `ferramentas/` (módulo JVM separado, fora do APK): gera `app/src/main/assets/snes_catalogo_mestre.json` a partir do DAT No-Intro

Detalhes em `docs/architecture.md` e `docs/modules.md`.

## Regras importantes

- **Idioma:** toda comunicação sobre o projeto é em português do Brasil.
- **Este CLAUDE.md é o contexto compartilhado** entre as duas máquinas (macOS M1 e Linux/NixOS, que é o ambiente principal com o Android Studio). A memória local do Claude Code não é compartilhada, então o que importa fica aqui ou em `docs/`.
- **JAVA_HOME no NixOS:** `java`/`python3`/`node`/`kotlinc` não ficam no PATH (use `nix-shell -p <pacote> --run "..."`). Todo `./gradlew` precisa de `export JAVA_HOME=/nix/store/i39marv4b6f5b1rfygp0vqfjrn5pqixy-openjdk-21.0.12+2`. Se esse caminho sumir, ache o atual com `find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d`.
- **JAVA_HOME no Mac:** o symlink do Homebrew `openjdk@17` está quebrado. Use o JBR do Android Studio (hoje Java 25, suportado pelo Gradle 9.8): `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Credenciais do ScreenScraper** ficam em `local.properties` (fora do git). No painel, "Usuário Dev" = `devid` e "Senha" = `devpassword`. **Não** use "Depurar senha", que é outra coisa (essa confusão já aconteceu).
- **Não use `maxwidth`** (nem outro redimensionamento do servidor) na URL de capa: o arquivo não fica menor e a capa sai cortada. Se precisar comprimir, faça isso no cliente, depois do download.
- **Capas em `context.noBackupFilesDir`** (`no_backup/capas/<id>.jpg`), nunca em `filesDir`. Senão estouram a cota de 25 MB do Auto Backup e derrubam o backup de `posse_usuario`.
- **Primeira sync troca o catálogo:** com `sincronizacao_status` vazia, a sync apaga `jogos` e `posse_usuario`, mas antes salva as posses em `filesDir/posses_pendentes_sync.json` e as reassocia pelo nome normalizado conforme os jogos chegam (`data/sync/PossesPendentes.kt`). Posse cujo nome não casar fica pendente no arquivo, não é perdida.
- **Manter `docs/` atualizado** ao fim de cada feature (os arquivos afetados, mais uma entrada em `docs/history.md`).

## Convenções de desenvolvimento

- Nomes de classes, funções, variáveis e commits em pt-br (`JogoRepository`, `salvarPosse`, `caminhoCapaLocal`).
- Sem framework de DI: usa-se o padrão manual `companion object.obterInstancia(context)` (em `AppDatabase`, `JogoRepository` e `SincronizacaoRepository`; `NetworkModule` é um `object` com `by lazy`) e Factories manuais nos ViewModels.
- Testes JUnit só de lógica pura (parsing, filtro, agrupamento, cálculo de checkpoint, heurística de cota; o mapper ainda não tem teste). Ainda não há teste de Room nem instrumentado. A verificação de UI é manual, no S25.
- Specs em `docs/superpowers/specs/` e planos em `docs/superpowers/plans/` (`AAAA-MM-DD-<tema>*.md`).
- Comandos:
  - Build: `./gradlew :app:assembleDebug`
  - Testes: `./gradlew test` (ou `:app:testDebugUnitTest`, `:ferramentas:test`)
  - Instalar: `adb -s RQCY70208AF install -r app/build/outputs/apk/debug/app-debug.apk`
  - Catálogo mestre: `./gradlew :ferramentas:run --args="'<.dat>' '<saída .json>'"`

## Documentação (`docs/`)

- `architecture.md`: camadas, fluxos e diagrama da sync
- `modules.md`: módulos, pacotes e responsabilidades de cada arquivo
- `database.md`: schema Room, entidades, DAOs e migrações
- `api.md`: endpoints do ScreenScraper usados e formato dos DTOs
- `business-rules.md`: regras de posse, filtros, sync, checkpoint e capas
- `integrations.md`: integração com o ScreenScraper e o DAT No-Intro
- `infrastructure.md`: ambientes Mac/NixOS, build e device
- `testing.md`: estratégia e cobertura de testes
- `dependencies.md`: dependências e versões
- `decisions.md`: decisões de arquitetura/produto (ADRs curtas)
- `history.md`: histórico detalhado de features, revisões, commits e Minor parked
- `superpowers/`: specs e planos de cada feature

## Pendências abertas

- Foto da própria cópia: o campo existe no modelo, mas falta a UI de câmera/picker (hoje é um placeholder).
- Os Minor parked e a investigação do contador de requisições do ScreenScraper estão em `docs/history.md`.
