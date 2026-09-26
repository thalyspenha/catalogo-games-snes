# Infraestrutura, build e ambiente

## Build do :app (`app/build.gradle.kts`)

| Item | Valor |
|---|---|
| `namespace` / `applicationId` | `com.thalys.catalogosnes` |
| `compileSdk` | 37 |
| `targetSdk` | 35 |
| `minSdk` | 26 |
| `versionCode` / `versionName` | 1 / `0.1` |
| Java source/target | `JavaVersion.VERSION_11` |
| Kotlin `jvmTarget` | `11` |
| `buildFeatures` | `compose = true`, `buildConfig = true` |
| Gradle wrapper | 9.8.0 |
| `gradle.properties` | `-Xmx2048m`, `org.gradle.parallel=true`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official` |

### Build types
- `release`: `isMinifyEnabled = false`, proguard `proguard-android-optimize.txt` + `app/proguard-rules.pro`.
- `debug`: padrão do AGP (sem customização). `BuildConfig.DEBUG` controla o nível de log HTTP (`BODY` em debug, `NONE` fora).
- Configuração de assinatura (`signingConfigs`) para release: Não identificado.
- Product flavors: Não identificado (nenhum).

### BuildConfig e credenciais
`app/build.gradle.kts` lê `local.properties` na raiz (se existir; arquivo está no `.gitignore`) e cria campos `String` no `BuildConfig`. Chave ausente → string vazia (o build não quebra).

| Chave em `local.properties` | Campo `BuildConfig` | Uso |
|---|---|---|
| `SCREENSCRAPER_DEVID` | `SCREENSCRAPER_DEVID` | `devid` da API |
| `SCREENSCRAPER_DEVPASSWORD` | `SCREENSCRAPER_DEVPASSWORD` | `devpassword` |
| `SCREENSCRAPER_SOFTNAME` | `SCREENSCRAPER_SOFTNAME` | `softname` |
| `SCREENSCRAPER_USER_ID` | `SCREENSCRAPER_USER_ID` | `ssid` (opcional) |
| `SCREENSCRAPER_USER_PASSWORD` | `SCREENSCRAPER_USER_PASSWORD` | `sspassword` (opcional) |

Lidos em `app/src/main/java/com/thalys/catalogosnes/data/remote/screenscraper/ScreenScraperCredenciais.kt`. Sem `devid`/`devpassword`, o sync termina em `SincronizacaoEstado.Erro`. Atenção: por ficarem no `BuildConfig`, os valores são embutidos no APK.

## AndroidManifest (`app/src/main/AndroidManifest.xml`)
- Permissões: apenas `android.permission.INTERNET`.
- Activity única `.MainActivity` (`exported = true`, launcher).
- Tema `@style/Theme.CatalogoSnes`, ícone `@mipmap/ic_launcher`, `supportsRtl = true`.
- `usesCleartextTraffic` / `networkSecurityConfig`: Não identificado (não declarados; a base URL é HTTPS).

## Backup
- `android:allowBackup="true"`, sem `fullBackupContent`/`dataExtractionRules` (regras padrão do Auto Backup).
- Capas baixadas vão para `noBackupFilesDir/capas/` (`CapaDownloader.kt`), portanto excluídas do backup; o banco Room (`catalogo_snes.db`) segue as regras padrão.

## Armazenamento local
- Banco Room `catalogo_snes.db`, versão 3, `exportSchema = false` (sem schemas JSON versionados).
- Capas: `noBackupFilesDir/capas/<jogoId>.jpg`.

## Ambientes de desenvolvimento (fonte: `CLAUDE.md`, não o código)
- macOS (M1): se `JAVA_HOME` estiver quebrado, usar o JBR do Android Studio: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- Linux NixOS (ambiente principal): `java` fora do PATH; exportar `JAVA_HOME` para um OpenJDK 21 do `/nix/store` (localizar com `find /nix/store -maxdepth 1 -iname "*openjdk-21*" -type d`).
- Device de teste: Samsung Galaxy S25.

## Docker / CI/CD
- Docker: Não identificado (nenhum Dockerfile/compose).
- CI/CD: Não identificado (sem `.github/`, GitLab CI, Jenkinsfile etc.).
- Publicação em loja: Não identificado.

## Comandos
```sh
./gradlew :app:assembleDebug      # APK debug
./gradlew test                    # testes JUnit de :app e :ferramentas
```

### Rodando :ferramentas
Gera `app/src/main/assets/snes_catalogo_mestre.json` a partir do DAT No-Intro de SNES (arquivo externo ao repositório):
```sh
./gradlew :ferramentas:run --args="'<caminho do .dat>' '<caminho de saída .json>'"
```
`main` exige exatamente 2 argumentos e imprime `Gerado N jogos únicos em <saída>`. A cópia do resultado para `app/src/main/assets/` é manual (Não identificado automação).
