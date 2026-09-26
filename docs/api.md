# API

## Endpoints expostos: nenhum

O app é um cliente Android puro (uma `MainActivity`, sem servidor, sem `Service`/`ContentProvider` exportados). Não expõe nenhuma API HTTP ou IPC.

## API consumida: ScreenScraper.fr (API v2)

Código em `app/src/main/java/com/thalys/catalogosnes/data/remote/screenscraper/`.

### `NetworkModule` (`NetworkModule.kt`)

| Item | Valor |
|---|---|
| Base URL | `https://www.screenscraper.fr/api2/` |
| Cliente | `OkHttpClient` (público, `by lazy`; reutilizado pelo `CapaDownloader`) |
| `connectTimeout` | 30 s |
| `readTimeout` | 30 s |
| `writeTimeout` | Não configurado (padrão do OkHttp) |
| Interceptors | Só `HttpLoggingInterceptor`: nível `BODY` se `BuildConfig.DEBUG`, senão `NONE`. Não há interceptor de autenticação (credenciais vão como query params em cada chamada). |
| Conversor | `kotlinx.serialization` via `asConverterFactory("application/json")` |
| Config `Json` | `ignoreUnknownKeys = true`, `coerceInputValues = true`, `isLenient = true` (necessário porque `systemesListe.php` devolve `id` numérico sem aspas) |
| Acesso | `NetworkModule.screenScraperApi` (singleton `by lazy`) |

Atenção: como o logging em debug é `BODY`, as URLs com `devpassword`/`sspassword` aparecem no logcat em builds de debug.

### Interface `ScreenScraperApi` (`ScreenScraperApi.kt`)

Constante: `SISTEMA_SNES = 4`.

Todos os endpoints são `GET`, `suspend`, e recebem os parâmetros comuns:

| Query | Tipo | Obrigatório |
|---|---|---|
| `devid` | String | sim |
| `devpassword` | String | sim |
| `softname` | String | sim |
| `ssid` | String? | não (default `null`) |
| `sspassword` | String? | não (default `null`) |
| `output` | String | default `"json"` |

#### `GET systemesListe.php` — `listarSistemas(...)`
- Só os parâmetros comuns.
- Retorno: `SistemasRespostaDto`.
- Uso no app: nenhum chamador encontrado (validado manualmente via curl, segundo comentários).

#### `GET jeuInfos.php` — `buscarInfoJogo(...)`
Parâmetros extras (todos opcionais): `systemeid: Int?`, `gameid: Long?`, `romnom: String?`, `romtaille: Long?`, `crc: String?`, `md5: String?`, `sha1: String?`.
- Retorno: `JogoInfoRespostaDto`.
- Uso no app: `SincronizacaoRepository.buscarComRetry` envia `systemeid=4`, `romnom`, `romtaille`, `crc` (do catálogo mestre) + credenciais.

#### `GET jeuRecherche.php` — `buscarJogoPorNome(...)`
Parâmetros extras: `systemeid: Int?` (opcional), `recherche: String` (obrigatório).
- Retorno: `JogoBuscaRespostaDto`.
- Uso no app: nenhum chamador encontrado.

### DTOs (`dto/ScreenScraperDtos.kt`)

Quase todos os campos são `String?` (a API devolve números como string); conversão ocorre no mapper.

| DTO | Campos principais |
|---|---|
| `HeaderDto` | `APIversion`, `dateTime`, `commandRequested`, `success`, `error` |
| `JogoInfoRespostaDto` | `header`, `response: JogoInfoBodyDto { jeu: JeuDto? }` |
| `JogoBuscaRespostaDto` | `header`, `response: JogoBuscaBodyDto { jeux: List<JeuDto>? }` |
| `SistemasRespostaDto` | `header`, `response: SistemasBodyDto { systemes: List<SistemaDto>? }` |
| `JeuDto` | `id`, `romid`, `noms: List<TextoRegionalDto>`, `synopsis: List<TextoIdiomaDto>`, `dates: List<TextoRegionalDto>`, `genres: List<GeneroDto>`, `classifications`, `developpeur`/`editeur`/`systeme: RefComTextoDto`, `medias: List<MidiaDto>`, `joueurs`/`note: TextoSimplesDto` |
| `TextoRegionalDto` | `region`, `text` |
| `TextoIdiomaDto` | `langue`, `text` |
| `RefComTextoDto` | `id`, `text` |
| `TextoSimplesDto` | `text` |
| `GeneroDto` | `id`, `nomcourt`, `principale`, `parentid`, `noms: List<TextoIdiomaDto>` |
| `ClassificacaoDto` | `type`, `text` |
| `MidiaDto` | `type`, `parent`, `url`, `region`, `crc`, `md5`, `sha1`, `size`, `format` |
| `SistemaDto` | `id`, `noms: NomesSistemaDto`, `compagnie`, `type`, `datedebut`, `datefin` |
| `NomesSistemaDto` | `nom_eu`, `nom_us`, `nom_jp`, `nom_recalbox`, `nom_retropie`, `nom_launchbox`, `nom_hyperspin`, `noms_commun` |

### Mapper (`ScreenScraperMapper.kt`) — `JeuDto` → `JogoEntity`

Constantes:
- `PRIORIDADE_REGIAO = ["us", "wor", "eu", "ss", "jp"]`
- `PRIORIDADE_IDIOMA = ["pt", "en", "wor"]`
- Tipos de capa: `"box-2D"`, `"box-3D"`

Função auxiliar `escolherPorChave(itens, prioridades)`: percorre as prioridades em ordem e devolve o `text` do primeiro item cuja chave bate; senão `null`.

Regras de `paraJogoEntity(jeu)` (retorna `null` se faltar id ou nome):

| Campo | Regra |
|---|---|
| `id` | `jeu.id.toLongOrNull()`; se null → retorna `null` |
| `nome` | `noms` por `PRIORIDADE_REGIAO`; senão primeiro `noms`; se nada → retorna `null` |
| `descricao` | `synopsis` por `PRIORIDADE_IDIOMA`; senão primeira sinopse |
| `anoLancamento` | `dates` por `PRIORIDADE_REGIAO`, senão primeira data; ano extraído com regex `(\d{4})` |
| `genero` | Só o **primeiro** item de `genres` (não usa `principale`); nome por `PRIORIDADE_IDIOMA`, senão primeiro nome |
| `desenvolvedora` | `developpeur.text` |
| `publicadora` | `editeur.text` |
| `regiao` | Primeira região de `PRIORIDADE_REGIAO` presente entre as regiões de `noms` |
| `urlCapa` | `escolherCapa(medias)` (abaixo) |
| `caminhoCapaLocal` | não definido (default `null`); preenchido depois pelo sync |

`escolherCapa(medias)`:
1. Filtra mídias com `type` `box-2D` ou `box-3D`.
2. Para cada região em `PRIORIDADE_REGIAO`: primeira `box-2D` daquela região.
3. Senão, para cada região em `PRIORIDADE_REGIAO`: primeira capa (2D ou 3D) daquela região.
4. Senão, primeira capa filtrada.
5. A URL é devolvida como veio da API, sem parâmetros adicionais (ex.: sem `maxwidth`).
