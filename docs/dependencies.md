# Dependências

Versões de `gradle/libs.versions.toml`. Gradle wrapper: **9.8.0** (`gradle/wrapper/gradle-wrapper.properties`).

## Plugins

| Plugin | ID | Versão | Onde |
|---|---|---|---|
| Android Application (AGP) | `com.android.application` | 9.4.1 | `:app` (Kotlin embutido: o AGP 9 compila Kotlin sem o plugin `kotlin.android`) |
| Kotlin Compose compiler | `org.jetbrains.kotlin.plugin.compose` | 2.4.20 | `:app` |
| Kotlin Serialization | `org.jetbrains.kotlin.plugin.serialization` | 2.4.20 | `:app`, `:ferramentas` |
| KSP | `com.google.devtools.ksp` | 2.3.12 | `:app` (compilador do Room) |
| Kotlin JVM | `org.jetbrains.kotlin.jvm` | 2.4.20 | `:ferramentas` (via `alias(libs.plugins.kotlin.jvm)`; declarado com `apply false` na raiz) |
| application (Gradle core) | `application` | — | `:ferramentas` (`mainClass = ferramentas.GerarCatalogoMestreSnesKt`) |

Repositórios: `google()`, `mavenCentral()` (+ `gradlePluginPortal()` para plugins), `FAIL_ON_PROJECT_REPOS`.

## Bibliotecas do :app

| Artefato | Versão | Para que serve / onde é usado |
|---|---|---|
| `androidx.core:core-ktx` | 1.19.1 | Declarada; nenhum import direto `androidx.core.*` achado no código (uso direto Não identificado) |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 | Declarada; nenhum uso direto (`lifecycleScope`, `repeatOnLifecycle`) achado |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | `viewModel(factory=...)` nas telas; `ViewModel`/`viewModelScope` nos ViewModels |
| `androidx.activity:activity-compose` | 1.13.0 | `ComponentActivity`, `setContent`, `enableEdgeToEdge` (`MainActivity.kt`), `BackHandler` (`TelaBiblioteca.kt`) |
| `androidx.navigation:navigation-compose` | 2.10.2 | `CatalogoNavHost.kt` |
| `androidx.compose:compose-bom` | 2026.09.00 | Plataforma que fixa versões dos artefatos Compose abaixo |
| `androidx.compose.ui:ui` | (BOM) | Compose UI em todas as telas |
| `androidx.compose.ui:ui-graphics` | (BOM) | `Color` etc. (`ui/theme/Color.kt`, `TelaBiblioteca.kt`) |
| `androidx.compose.ui:ui-tooling-preview` | (BOM) | `@Preview` em `TelaBiblioteca.kt` |
| `androidx.compose.ui:ui-tooling` | (BOM) | `debugImplementation`, suporte a preview no IDE |
| `androidx.compose.material:material-icons-core` | via BOM (1.7.8) | Ícones `Icons.*` (`TelaBiblioteca`, `TelaDetalheJogo`); o Material3 novo não traz mais os ícones de forma transitiva |
| `androidx.compose.material3:material3` | (BOM) | Componentes Material 3 (Scaffold, Drawer, Card...), tema |
| `androidx.room:room-runtime` | 2.8.5 | Banco local (`data/local/`) |
| `androidx.room:room-ktx` | 2.8.5 | Suporte a DAOs `suspend` e `Flow` |
| `androidx.room:room-compiler` | 2.8.5 | via `ksp(...)`, geração do código Room |
| `com.squareup.retrofit2:retrofit` | 2.12.0 | `ScreenScraperApi`, `NetworkModule`; `HttpException` em `SincronizacaoRepository` |
| `com.squareup.retrofit2:converter-kotlinx-serialization` | 2.12.0 | `asConverterFactory` em `NetworkModule` |
| `com.squareup.okhttp3:logging-interceptor` | 4.12.0 | `HttpLoggingInterceptor` em `NetworkModule` (traz OkHttp, usado também por `CapaDownloader`) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | DTOs da API, seed, catálogo mestre |
| `io.coil-kt:coil-compose` | 2.7.0 | `AsyncImage` em `TelaBiblioteca.kt` e `TelaDetalheJogo.kt` |
| `junit:junit` | 4.13.2 | `testImplementation` (testes unitários) |

Transitivas usadas diretamente no código:
- `androidx.lifecycle.compose.collectAsStateWithLifecycle` (artefato `lifecycle-runtime-compose`) — usado nas 3 telas, mas não declarado explicitamente; chega transitivamente (qual dependência o traz: Não identificado sem rodar `./gradlew dependencies`).
- `kotlinx-coroutines` (Flow, `Dispatchers`, `suspendCancellableCoroutine`) — não declarado; transitivo (via Room/lifecycle).
- `okhttp3` core — transitivo via `logging-interceptor`/Retrofit.

## Bibliotecas do :ferramentas

| Artefato | Versão | Uso |
|---|---|---|
| `kotlinx-serialization-json` | 1.11.0 | Escrita do JSON de saída |
| `junit` | 4.13.2 | `AgrupamentoClonesTest` |

XML é lido com `javax.xml.parsers` (JDK), sem dependência externa.
