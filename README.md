# Catálogo de Jogos SNES

App Android para catalogar jogos de Super Nintendo (SNES). Projeto pessoal, feito para o hobby do
autor com games retro/repro.Dispositivo de teste
principal: Samsung Galaxy S25.

A ideia é ter uma biblioteca completa dos jogos de SNES (não só os que o usuário possui), com capa
e descrição, em estilo "Netflix" (grid navegável por categorias), permitindo marcar quais jogos já
tem, quais quer ter e em que condição estão, tudo salvo localmente para consulta offline (por
exemplo, em lojas e feiras de repro sem sinal de internet).

## Funcionalidades

### Já funciona hoje

- Grid principal (`TelaBiblioteca`) listando os jogos salvos no Room, com capa (via Coil) e um selo
  indicando o status de posse de cada jogo.
- Tela de detalhe do jogo (`TelaDetalheJogo`) com ano, gênero, desenvolvedora/publicadora e
  descrição.
- Edição do status de posse por jogo: **Tenho** / **Quero ter** / **Não me interessa**.
- Edição de completude CIB (Cartucho / Caixa / Manual) via checkboxes.
- Campo de nota de condição (texto livre).
- Navegação entre biblioteca e detalhe (Navigation Compose).
- Persistência local em Room (offline), com um catálogo seed de 25 jogos reais de SNES carregado de
  `app/src/main/assets/jogos_seed.json` na primeira execução.

### Planejado / ainda não implementado

- Integração real com a API do ScreenScraper para popular o catálogo completo de jogos de SNES
  (hoje só existe o esqueleto de rede — API, DTOs e mapper — sem chamadas reais testadas, aguardando
  credenciais).
- Carrosséis por categoria (gênero, ano, "meus jogos", "faltam") — hoje a biblioteca é um grid único.
- Captura/seleção de foto da própria cópia do jogo (o campo já existe no modelo, falta a UI de
  câmera/picker).
- Filtros e busca na biblioteca.

## Stack técnica

- Kotlin 2.0.21
- Jetpack Compose, com Compose BOM 2024.10.01
- Navigation Compose 2.8.4
- Room 2.6.1 (persistência local/offline)
- Retrofit 2.11.0 + OkHttp 4.12.0 (client HTTP para a API do ScreenScraper)
- kotlinx.serialization 1.7.3 (parsing JSON, tanto da API quanto do seed local)
- Coil 2.7.0 (carregamento/cache de imagens de capa)
- KSP 2.0.21-1.0.28 (processamento de anotações do Room)
- Android Gradle Plugin 8.7.2

compileSdk / targetSdk 35, minSdk 26.

## Como rodar

Pré-requisitos: Android SDK instalado e configurado, com `sdk.dir` apontando para ele em um arquivo
`local.properties` na raiz do projeto (esse arquivo não é versionado).

Build via linha de comando:

```bash
./gradlew assembleDebug
```

Também é possível abrir o projeto diretamente no Android Studio e rodar em um emulador ou
dispositivo físico com Android correspondente ao minSdk 26 (Android 8.0) ou superior.

## Estrutura de pastas

Pacote base: `com.thalys.catalogosnes`.

```
app/src/main/java/com/thalys/catalogosnes/
├── MainActivity.kt              # Activity única, hospeda o NavHost
├── data/
│   ├── local/                   # Room: entidades, DAOs, AppDatabase, seed local (JSON de assets)
│   ├── model/                   # Modelos de domínio (ex.: enum StatusPosse)
│   ├── remote/screenscraper/    # Camada de rede (Retrofit) para a API do ScreenScraper
│   └── repository/              # Repository que unifica Room + (futuramente) ScreenScraper
└── ui/
    ├── biblioteca/               # Grid principal + ViewModel
    ├── detalhe/                  # Tela de detalhe/edição de posse + ViewModel
    ├── navigation/               # Grafo de navegação (Navigation Compose)
    └── theme/                    # Tema Compose (paleta escura estilo "Netflix")
```

## Fonte de dados

O catálogo de jogos vem do [ScreenScraper.fr](https://www.screenscraper.fr/), escolhido por ser a
referência da comunidade retro/repro, com boa cobertura de SNES e capas em várias versões regionais
(US/EU/JP). A estratégia é baixar os dados uma vez (batch) e persistir localmente no Room, evitando
depender de internet no dia a dia do app.

Essa integração ainda está pendente: o esqueleto de rede (Retrofit, DTOs, mapper) já existe em
`data/remote/screenscraper/`, mas nenhuma chamada real foi testada, pois faltam as credenciais de
desenvolvedor (`devid`/`devpassword`) do ScreenScraper, que devem ser configuradas em
`local.properties`. Enquanto isso, o app usa um catálogo seed local de exemplo (25 jogos, em
`app/src/main/assets/jogos_seed.json`).
