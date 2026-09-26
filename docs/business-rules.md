# Regras de negócio

Extraídas do código em `main` (2026-09-26). Caminhos relativos a `app/src/main/java/com/thalys/catalogosnes/` salvo indicação.

## Posse do usuário

- **Status** — enum com 3 valores: `TENHO`, `QUERO_TER`, `NAO_INTERESSA` (`data/model/StatusPosse.kt`). Um jogo sem linha em `posse_usuario` fica "sem status" (`JogoComPosse.posse == null`).
- **Uma posse por jogo** — `PosseUsuarioEntity.jogoId` é a chave primária, com FK para `jogos.id` (`data/local/PosseUsuarioEntity.kt`). Salvar usa `REPLACE`, ou seja, cria ou sobrescreve (`data/local/PosseUsuarioDao.kt:salvar`).
- **Só salva com status escolhido** — `salvar()` retorna sem fazer nada se `status == null` (`ui/detalhe/DetalheJogoViewModel.kt:salvar`).
- **Completude CIB** — três booleanos independentes, todos `false` por padrão: `temCartucho`, `temCaixa`, `temManual` (`PosseUsuarioEntity`). Não existe regra que derive "CIB completo" nem que amarre CIB ao status (dá para marcar CIB com status `QUERO_TER`, por exemplo).
- **Nota de condição** — texto livre. Nota em branco é salva como `null` (`DetalheJogoViewModel.salvar`: `notaCondicao.ifBlank { null }`).
- **Foto da cópia** — é um **placeholder**. O campo `caminhoFoto` existe no modelo e é preservado ao salvar, mas nenhuma UI grava valor nele. A tela mostra o texto "Foto da cópia: em breve" (`ui/detalhe/TelaDetalheJogo.kt`, TODO por volta da linha 173).
- **`atualizadoEm`** — recebe `System.currentTimeMillis()` a cada salvamento (`DetalheJogoViewModel.salvar`).
- **Remoção de posse** — existe `JogoRepository.removerPosse(jogoId)` → `PosseUsuarioDao.remover`, mas **nenhuma tela chama esse método**. Pela UI, não há como voltar um jogo a "sem status"; dá apenas para trocar entre os 3 status.

## Catálogo e ordenação

- A biblioteca é ordenada por `nome ASC`, direto no SQL (`data/local/JogoDao.kt:observarBibliotecaCompleta`). Os filtros e a busca preservam essa ordem.
- Um jogo precisa ter no mínimo `id` numérico e nome. Sem isso, o mapper devolve `null` (`data/remote/screenscraper/ScreenScraperMapper.kt:paraJogoEntity`).
- Seed inicial: `assets/jogos_seed.json` é inserido uma única vez, na criação do banco (`data/local/AppDatabase.kt:construir`, `Callback.onCreate`).

## Filtros (`ui/biblioteca/FiltroBiblioteca.kt`)

- Fica ativo **um filtro por vez** (sealed class `FiltroBiblioteca`): `Todos`, `Tenho`, `QueroTer`, `Faltam`, `Genero(valor)`, `Ano(valor)`. A home começa sempre com `Todos`.
- `Tenho` / `QueroTer`: `posse.status` igual ao valor correspondente.
- **`Faltam`**: jogos cujo status **não é** `TENHO` **e não é** `NAO_INTERESSA`. Isso inclui os jogos sem posse (`null`) e os `QUERO_TER`. A duplicação com `QueroTer` é proposital (`filtrarBiblioteca`).
- `Genero`: comparação exata. Um gênero `null` ou em branco equivale a "Sem gênero".
- `Ano`: comparação exata com `anoLancamento.toString()`. `null` equivale a "Sem ano".
- `generosDisponiveis`: lista em ordem A-Z, com "Sem gênero" no fim só quando existe algum jogo sem gênero. `anosDisponiveis`: lista em ordem cronológica, com "Sem ano" no fim nas mesmas condições.

## Busca por nome

- Faz `trim()` da consulta e procura substring com `contains(ignoreCase = true)`, **sem remover acentos** (`FiltroBiblioteca.kt:filtrarPorNome`).
- **Ignora o filtro ativo**: a busca roda sempre sobre a lista inteira. Consulta em branco resulta em `resultadoBusca = null`, e a tela mostra o grid do filtro (`ui/biblioteca/BibliotecaViewModel.kt`, `combine`).

## Sincronização (`data/sync/SincronizacaoRepository.kt`)

- **Pré-condições**: se já existe um sync `EmAndamento`, a chamada é ignorada (trava de reentrância, não atômica). Sem credenciais de dev, o estado vai para `Erro`.
- **Primeira execução / sem checkpoint**: se `sincronizacao_status` está vazia, a sync salva as posses existentes em `filesDir/posses_pendentes_sync.json` (`PossesPendentesStore`, junto com pendências antigas), depois executa `posseUsuarioDao.limparTudo()` e `jogoDao.limparTudo()` (posse primeiro, por causa da FK). A cada jogo inserido pela sync, a posse pendente de mesmo nome normalizado (`normalizarNomeParaCasamento`: minúsculas, sem acento, sem pontuação, sem "the") é regravada com o id novo e sai do arquivo. Pendências sem casamento ficam no arquivo e são tentadas de novo nas syncs seguintes (`data/sync/PossesPendentes.kt`). Motivo: os ids do seed (1..25) não são ids do ScreenScraper, então não dá para casar por id.
- **Retomada**: processa só os itens do catálogo mestre cujo CRC ainda não tem `SUCESSO`. Itens com `FALHA` são tentados de novo em toda rodada (`data/sync/CalculoRestante.kt:calcularRestante`).
- **Throttle** de 1200 ms antes de cada item.
- **Resultado por item** (`buscarComRetry`):
  - `Sucesso`: a resposta tem `response.jeu`; zera o contador de falhas consecutivas (inclusive quando o mapper falha). Se o mapper devolve `null`, grava `FALHA` ("Resposta sem id/nome válidos"). Caso contrário, insere o jogo (REPLACE) e grava `SUCESSO` com o `jogoId`.
  - `NaoEncontrado`: resposta sem `jeu`. Grava `FALHA` ("Jogo não encontrado no ScreenScraper") e zera o contador.
  - `ErroDeRede`: 3 tentativas com backoff de 2 s e 4 s. Grava `FALHA` com a mensagem e incrementa o contador de falhas consecutivas.
  - `CotaEsgotada`: vale para HTTP 429/430/431, ou para `header.error` contendo "quota" ou "limite" (`cotaEsgotada`). Encerra com o estado `CotaEsgotada`.
- **Disjuntor**: 5 erros de rede consecutivos encerram o sync, também com o estado `CotaEsgotada`.
- **Cancelamento**: `CancellationException` é repassada sem retry. Se o sync termina sem estado terminal, volta a `Ocioso`.
- **Conclusão**: estado `Concluido(sucesso, falhas)`, em que as falhas trazem o nome de exibição (ou o CRC) e o motivo.
- **Capa local** (`data/sync/CapaDownloader.kt`): best-effort. A imagem é salva em `noBackupFilesDir/capas/<jogoId>.jpg` (sempre com extensão `.jpg`). Qualquer falha retorna `null`, sem log, e não marca o item como FALHA. O download é cancelável (`enqueue` + `suspendCancellableCoroutine`).
- **Exibição da capa**: usa o arquivo local se `caminhoCapaLocal` não for nulo (não verifica se o arquivo existe em disco); senão, `urlCapa` (`data/local/JogoEntity.kt:modeloCapa`). A escala é `ContentScale.Fit` (`TelaBiblioteca.kt`, `TelaDetalheJogo.kt`).

## Mapeamento ScreenScraper (`ScreenScraperMapper.kt`)

- Prioridade de região: `us, wor, eu, ss, jp`. Prioridade de idioma: `pt, en, wor`. Na falta de todos, usa o primeiro item da lista.
- Nome, data e região seguem a prioridade de região. Sinopse e gênero seguem a de idioma. Fica **só o primeiro gênero** da lista.
- O ano é o primeiro grupo de 4 dígitos encontrado na data.
- Capa (`escolherCapa`): primeiro busca `box-2D` na ordem de região. Depois, qualquer `box-2D`/`box-3D` na ordem de região. Por último, a primeira capa disponível. A URL é usada sem parâmetros (sem `maxwidth`).

## Catálogo mestre / deduplicação (`ferramentas/src/main/kotlin/ferramentas/GerarCatalogoMestreSnes.kt`)

- Só entram entradas do DAT No-Intro cujo conjunto de categorias é **exatamente** `{"Games"}`. Ficam de fora as que também têm Preproduction ou Demos.
- As entradas são agrupadas por `cloneofid` (ou pelo próprio id, quando não é clone). O representante do grupo é a raiz, se ela for elegível; senão, o clone elegível de menor id.
- A saída é ordenada por `nomeExibicao` (`agruparEDeduplicar`). Resultado atual: 1763 jogos em `app/src/main/assets/snes_catalogo_mestre.json`.
