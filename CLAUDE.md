# Catálogo de Jogos SNES

App Android para catalogar jogos de Super Nintendo (SNES), feito para o hobby do usuário com games retro/repro. Uso pessoal, alvo de teste principal: Samsung Galaxy S25.

## Idioma

Toda comunicação sobre este projeto deve ser em português do Brasil (pt-br).

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
- Linux — ambiente principal, com Android Studio completo

Por isso o contexto do projeto fica registrado aqui neste `CLAUDE.md` (versionado no git), em vez de depender de memória local do Claude Code, que não é compartilhada entre máquinas.

## Estrutura do projeto

- `app/build.gradle.kts` — módulo Android, Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 26
- `gradle/libs.versions.toml` — catálogo de versões (Compose BOM 2024.10.01, Room 2.6.1, Retrofit 2.11.0, Coil 2.7.0)
- `app/src/main/java/com/thalys/catalogosnes/`
  - `MainActivity.kt` — tela inicial provisória (grid Compose com jogos de exemplo hardcoded, sem dados reais ainda)
  - `ui/theme/` — tema Compose (paleta escura estilo "Netflix")
  - `data/local/` — Room: `JogoEntity` (metadados do catálogo), `PosseUsuarioEntity` (status pessoal: TENHO/QUERO_TER/NAO_INTERESSA, completude CIB, foto, nota de condição), `JogoComPosse` (relação), DAOs, `AppDatabase`
  - `data/model/StatusPosse.kt` — enum de status de posse

Pacote base: `com.thalys.catalogosnes`. Build verificado com `./gradlew assembleDebug` (sucesso).

## Status atual

Projeto Android inicial montado e compilando (2026-07-29). Falta:
- Integrar API do ScreenScraper (Retrofit) para popular `JogoEntity` com a biblioteca completa de jogos de SNES.
- Conectar a tela (`MainActivity`/`TelaBiblioteca`) aos dados reais do Room em vez da lista de exemplo hardcoded.
- Telas de detalhe do jogo e de edição de status de posse (toggle Tenho/Quero ter/Não interessa, CIB, foto, nota).
- Cadastro/chave de API do ScreenScraper (ainda não criada).
