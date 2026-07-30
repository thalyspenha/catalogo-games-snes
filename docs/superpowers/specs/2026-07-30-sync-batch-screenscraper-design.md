# Sync em batch do catálogo de SNES via ScreenScraper

Data: 2026-07-30
Status: aprovado, aguardando plano de implementação

## Contexto

O app já tem um catálogo de bootstrap com 25 jogos reais de SNES (`app/src/main/assets/jogos_seed.json`), populado no Room via `RoomDatabase.Callback.onCreate`. A camada de rede do ScreenScraper (`ScreenScraperApi`, DTOs, `NetworkModule`, `ScreenScraperMapper`, `ScreenScraperCredenciais`) está pronta e foi validada contra a API real em 2026-07-30 (credenciais funcionando, DTOs corrigidos com JSON real).

O objetivo desta spec é desenhar o fluxo que baixa o catálogo **completo** de jogos de SNES do ScreenScraper (não só os 25 do seed) e substitui o seed por dados reais, persistidos no Room, seguindo a estratégia já definida no escopo do produto: baixar uma vez, persistir localmente, não bater na API a cada uso.

### Restrição descoberta durante o brainstorming

A API do ScreenScraper **não tem um endpoint para listar todos os jogos de um sistema**. Confirmado em duas fontes: a documentação oficial (`webapi2.php`) só lista endpoints de metadados de referência (gêneros, regiões, mídias, etc.); e o Skyscraper (scraper de referência em C++) identifica jogo por jogo via `jeuInfos.php` com CRC/MD5/nome de arquivo de ROM — nunca lista o catálogo inteiro de um sistema.

Por isso, o sync precisa de uma **lista mestra externa** de jogos oficiais de SNES para guiar, jogo por jogo, as chamadas ao `jeuInfos.php`. O usuário forneceu um DAT No-Intro real (`Nintendo - Super Nintendo Entertainment System.dat`, schema `schema_nointro_datfile_v4.xsd`, versão `20260729-015337`), validado: 4128 entradas `<game>`, cada uma com 1 ou 2 tags `<category>`. Distribuição real dos conjuntos de categoria (contados por `<game>`, não por tag):

| Conjunto de categorias | Qtde |
|---|---|
| `Games` (só) | 3314 |
| `Games, Preproduction` | 662 |
| `Demos, Games` | 14 |
| `Applications` | 40 |
| `Educational` | 19 |
| `Miscellaneous` | 9 |
| `Add-Ons` | 9 |
| `Applications, Preproduction` | 6 |
| `Educational, Preproduction` | 4 |
| `Demos` | 4 |
| `Preproduction, Miscellaneous` | 1 |

Achado importante: **662 entradas trazem `Games` E `Preproduction` ao mesmo tempo** (são os betas/protótipos, ex: `"90 Minutes - European Prime Goal (Europe) (Beta)"` tem `<category>Games</category>` **e** `<category>Preproduction</category>`), e mais 14 trazem `Games`+`Demos`. Um filtro ingênuo "categoria contém Games" deixaria passar 676 betas/demos, contrariando a decisão do usuário de excluir protótipo/beta/demo. O filtro correto é **conjunto de categorias igual a exatamente `{Games}`** (uma única tag, e é `Games`) — resultando em **3314 entradas elegíveis**.

Dessas 3314, **2234 têm atributo `cloneofid`** (variantes regionais/revisões do mesmo jogo). Agrupando por `cloneofid` (ou pelo próprio id quando não tem clone) dentro só das 3314 elegíveis, o número real de jogos únicos é **1763** — esse é o tamanho esperado do catálogo mestre final.

### Decisões tomadas com o usuário

- Filtrar só a categoria `Games` do DAT (descarta protótipo/beta/demo/app/educacional).
- Deduplicar por `cloneofid`: cada grupo de clones vira 1 jogo único no catálogo.
- Gatilho do sync: botão manual numa tela dedicada (não roda sozinho na primeira execução, não usa WorkManager).
- Se o app for fechado/interrompido no meio do sync, retomar de onde parou na próxima execução (não refazer o catálogo inteiro).
- Jogos não encontrados no ScreenScraper (ou com erro após retries): pular e seguir, listar falhas ao final, permitir tentar de novo só essas.
- Progresso mostrado numa tela dedicada com barra + contador (não notificação de sistema/foreground service).
- Dados de posse (Tenho/Quero ter/etc.) já marcados hoje são de teste — pode substituir o catálogo inteiro sem migração especial ao rodar o sync completo.
- O `.dat` bruto (1,8MB de XML) não entra no app: é pré-processado uma vez por um script externo, virando um JSON enxuto como asset.

## Arquitetura

```
módulo Gradle :ferramentas (Kotlin JVM, offline, roda manualmente)
        │  lê o .dat, filtra categoria "Games", agrupa por cloneofid
        ▼
app/src/main/assets/snes_catalogo_mestre.json   (novo asset, ~1 entrada por jogo único)
        │  lido em runtime
        ▼
SincronizacaoRepository (novo, data/sync/)
        │  jeuInfos.php por item (romnom/crc/romtaille) → ScreenScraperMapper → JogoDao
        │  grava progresso em SincronizacaoStatusEntity (Room)
        ▼
SincronizacaoViewModel + TelaSincronizacao (nova, ui/sincronizacao/)
        │  observa StateFlow de progresso, botão iniciar/cancelar/retry
```

Nenhuma peça existente (`JogoRepository`, `AppDatabase` além da tabela nova, UI de biblioteca/detalhe, Navigation existente) muda de comportamento — o sync é aditivo.

### Lacuna encontrada: falta a permissão de INTERNET

`app/src/main/AndroidManifest.xml` não declara `<uses-permission android:name="android.permission.INTERNET" />`. Isso nunca deu problema até agora porque a camada de rede só foi exercitada via `curl` fora do app (ver validação de credenciais de 2026-07-30) — o `./gradlew assembleDebug` compila normalmente sem essa permissão, mas qualquer chamada Retrofit feita de dentro do app instalado falharia em runtime com `SecurityException`. Corrigir isso é pré-requisito para o sync funcionar de verdade no dispositivo, então entra como primeira tarefa do plano.

## 1. Pré-processamento do DAT (script externo)

Novo módulo Gradle `:ferramentas` (plugin `kotlin("jvm")`, sem dependência do Android), **não empacotado no APK**, versionado no repo para poder ser re-executado (`./gradlew :ferramentas:run`) se sair uma versão mais nova do DAT No-Intro. Kotlin JVM puro em vez de um script Python/Node: o ambiente Linux deste projeto é NixOS, onde `python3`/`node` não ficam no PATH direto mas são alcançáveis via `nix-shell -p <pacote> --run` — ou seja, Python era uma opção viável, não uma restrição. A escolha por Kotlin foi por consistência de toolchain (mesma linguagem/build do resto do app, testável com JUnit no mesmo `./gradlew`), não por falta de alternativa.

Passos do script:
1. Parseia o XML do `.dat` (todas as entradas `<game>`, independente de categoria, para conhecer a relação completa de clones).
2. Marca cada `<game>` como elegível se seu conjunto de `<category>` for **exatamente `{Games}`** (uma tag só, e é "Games" — exclui os 662 `Games+Preproduction` e os 14 `Games+Demos`, ver tabela acima).
3. Agrupa por família: a chave do grupo é o `cloneofid` quando existe, ou o próprio `id` do jogo quando não existe (ele é a raiz).
4. Para cada grupo, filtra os membros elegíveis (passo 2). Se nenhum membro do grupo for elegível, o grupo inteiro é descartado (ex: família só tinha beta/protótipo/demo).
5. Escolhe 1 representante por grupo: preferir o jogo sem `cloneofid` (a raiz) se ele for elegível; senão, usar o de menor `id` entre os membros elegíveis que sobraram.
6. Do representante, extrai: `romNome` (atributo `name` do `<rom>`), `crc` (atributo `crc` do `<rom>`), `romTamanho` (atributo `size` do `<rom>`, inteiro), `nomeExibicao` (atributo `name` do `<game>`).
7. Escreve `app/src/main/assets/snes_catalogo_mestre.json` como uma lista JSON desses objetos, ordenada por `nomeExibicao`.

Rodando esse algoritmo contra o `.dat` real: **3314 entradas elegíveis → 1763 jogos únicos** no catálogo mestre final (números confirmados por análise do arquivo real durante o planejamento, não uma estimativa).

Formato de saída:
```json
[
  {
    "romNome": "Super Mario World (USA).sfc",
    "crc": "b19ed489",
    "romTamanho": 524288,
    "nomeExibicao": "Super Mario World (USA)"
  }
]
```

## 2. Novo Room: status de sincronização

```kotlin
@Entity(tableName = "sincronizacao_status")
data class SincronizacaoStatusEntity(
    @PrimaryKey val crc: String,
    val status: StatusSincronizacao,
    val jogoId: Long?,
    val mensagemErro: String?,
)

enum class StatusSincronizacao { SUCESSO, FALHA }
```

Uma linha só existe depois de uma tentativa real de sincronizar aquele `crc` — "pendente" é simplesmente a ausência de linha. Isso resolve retomada e re-tentativa de falhas com uma única query:

- **Restante a sincronizar** = catálogo mestre (asset) − crcs com `status = SUCESSO`.
- **Retomar** e **tentar novamente falhas** usam exatamente esse mesmo cálculo (falhas não têm `SUCESSO`, então voltam a entrar na lista).

`AppDatabase` sobe para `version = 2` com uma `Migration` que só cria a tabela nova (sem dados de produção reais a preservar, por decisão do usuário).

## 3. `SincronizacaoRepository`

Novo pacote `data/sync/`. Segue o mesmo padrão manual do resto do projeto (`companion object.obterInstancia(context)`, sem DI framework).

Responsabilidades:
- `carregarCatalogoMestre(context): List<CatalogoMestreItem>` — parseia `snes_catalogo_mestre.json` dos assets (kotlinx.serialization, mesmo padrão do `SeedLoader`).
- `fun observarEstado(): StateFlow<SincronizacaoEstado>`, onde `SincronizacaoEstado` é um sealed class: `Ocioso`, `EmAndamento(atual: Int, total: Int, nomeJogoAtual: String)`, `Concluido(sucesso: Int, falhas: Int)`, `CotaEsgotada(sucesso: Int, restantes: Int)`.
- `suspend fun sincronizar()`, chamada dentro do `viewModelScope` do `SincronizacaoViewModel`:
  1. Se a tabela `sincronizacao_status` estiver vazia (primeira execução real do fluxo), limpa `jogos` e `posse_usuario` antes de começar.
  2. Carrega o catálogo mestre e a lista de crcs já `SUCESSO`; calcula a lista restante.
  3. Para cada item restante, sequencialmente:
     - `delay(1200)` (throttle recomendado pela API).
     - Chama `screenScraperApi.buscarInfoJogo(devId, devPassword, softName, ssid, sspassword, systemeId = ScreenScraperApi.SISTEMA_SNES, romNome = item.romNome, romTamanho = item.romTamanho, crc = item.crc)`. `ssid`/`sspassword` vêm de `ScreenScraperCredenciais.usuarioId`/`usuarioSenha` — hoje em branco (`String?` nulo/vazio), a API já trata como opcionais.
     - Erro de rede/timeout: até 2 retries com backoff curto (2s, 4s) antes de desistir do item.
     - Resposta "jogo não encontrado": vai direto para `FALHA`, sem retry.
     - Resposta indicando cota diária esgotada: grava o progresso já feito, emite `CotaEsgotada` e **para o sync inteiro** (não marca os itens restantes como falha — eles continuam "pendentes" para a próxima execução). O texto/código exato que a API usa pra sinalizar cota esgotada ainda não foi observado com uma cota real estourada; validar contra uma resposta real durante a implementação e ajustar a detecção se necessário.
     - Sucesso: mapeia com `ScreenScraperMapper.paraJogoEntity`, insere/atualiza via `JogoDao`, grava `SincronizacaoStatusEntity(crc, SUCESSO, jogoId, null)`.
     - Falha (não encontrado ou retries esgotados): grava `SincronizacaoStatusEntity(crc, FALHA, null, mensagemErro)`.
     - Emite `EmAndamento(atual, total, nomeExibicao do item)` a cada iteração.
  4. Ao final (percorreu todos os restantes sem esgotar cota), emite `Concluido(sucesso, falhas)`.
- Cancelamento: cooperativo, via `Job` do `viewModelScope` cancelado quando o usuário sai da tela ou aperta "Cancelar". Como cada sucesso já grava `SincronizacaoStatusEntity` antes de seguir pro próximo item, o progresso até o ponto de cancelamento nunca se perde — a próxima chamada a `sincronizar()` retoma dali.
- "Tentar novamente falhas": mesma função `sincronizar()`, sem parâmetro especial — falhas não têm `SUCESSO` gravado, então voltam a entrar na lista de restantes automaticamente.

## 4. UI

`ui/sincronizacao/TelaSincronizacao.kt` + `SincronizacaoViewModel.kt`, seguindo o mesmo padrão das telas existentes (`TelaBiblioteca`/`TelaDetalheJogo`).

Elementos da tela:
- Barra de progresso determinada + texto "X de ~N jogos".
- Nome do jogo sendo buscado no momento (`nomeExibicao` do item atual).
- Botão contextual: "Iniciar sincronização" (estado `Ocioso`) / "Cancelar" (estado `EmAndamento`) / "Tentar novamente falhas" (estado `Concluido` com `falhas > 0`).
- Ao concluir: resumo "X sincronizados, Y falharam", com lista simples dos nomes que falharam e o motivo.
- Se `CotaEsgotada`: aviso "cota diária da API esgotada, tente novamente mais tarde" no lugar do resumo de conclusão.

Navegação: novo ícone (ex: refresh/sync) na `TopAppBar` da `TelaBiblioteca`, navegando para a nova rota `"sincronizacao"` registrada em `CatalogoNavHost.kt`.

## 5. O que não muda

- `jogos_seed.json` / `SeedLoader` / o callback `onCreate` do `AppDatabase` continuam exatamente como estão hoje — servem de bootstrap inicial, para a biblioteca não ficar vazia antes do primeiro sync manual.
- `JogoRepository` não muda: continua desacoplado da origem dos dados (seed ou sync real), só passa a observar o que o sync gravou.
- UI de biblioteca/detalhe/edição de posse não muda, além do novo ponto de entrada para a tela de sync.

## 6. Testes

- Unitário do agrupamento por `cloneofid` no script de pré-processamento (conferir que a contagem de grupos e a escolha de representante batem com uma amostra conhecida do DAT).
- Unitário do cálculo "restante = catálogo mestre − crcs com SUCESSO" no `SincronizacaoRepository` (casos: primeira execução, retomada parcial, retry de falhas).
- `./gradlew assembleDebug` deve continuar passando.
- Teste manual: rodar um sync parcial (ex: cancelar depois de ~10 jogos), fechar/reabrir o app, retomar, e confirmar que os 10 primeiros não são refeitos.

## Fora de escopo

- Download/cache de imagens de capa durante o sync — já é responsabilidade do Coil, carregado sob demanda quando a tela exibe o jogo (comportamento atual, não muda).
- Migração de dados de posse do seed antigo para o catálogo real — descartada por decisão do usuário (dados de teste).
- WorkManager / sync em background — descartado nesta spec; pode ser revisitado depois se o uso real mostrar necessidade.
