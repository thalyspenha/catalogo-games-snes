# Índice de navegação e "ver tudo" na biblioteca

Data: 2026-07-30
Status: aprovado, aguardando plano de implementação

## Contexto

A feature de carrosséis por categoria (`docs/superpowers/specs/2026-07-30-carrosseis-biblioteca-design.md`, mergeada em `main`) foi implementada e passou pela revisão final, mas o próprio revisor achou um gap de design ao considerar o catálogo real (1763 jogos, não os 25 do seed):

- A linha **"Faltam"** vira uma tira horizontal de ~1700 cards — quase o catálogo inteiro hoje, sem paginação nem forma de "ver tudo". Só dá pra rolar horizontalmente card por card.
- O bloco **"Ano"** fica atrás de dezenas de linhas de **Gênero** (uma por valor distinto encontrado no catálogo). Chegar num ano específico exige rolar verticalmente por todas as linhas de gênero antes.

Não é bug — a implementação bate exatamente com o spec anterior. É uma limitação do próprio design, identificada só depois de rodar contra o volume real de dados. Esta spec desenha a correção.

## Decisões tomadas com o usuário

- Ataca os dois problemas juntos (tamanho de linha + acesso ao bloco de Ano), não em specs separadas.
- **Linhas grandes:** cap de **20 jogos visíveis** por linha, para as 4 categorias igualmente (Meus jogos, Faltam, cada linha de Gênero, cada linha de Ano). Linha com mais de 20 ganha um card **"Ver tudo"** no fim do `LazyRow`.
- **"Ver tudo"** abre uma tela nova com o grid de 3 colunas que existia antes dos carrosséis (mesmo card, mesma UI), filtrado só pra aquela categoria — reaproveita o layout antigo em vez de inventar um novo.
- **Acesso ao bloco de Ano:** não foi resolvido reordenando os blocos (Ano antes de Gênero) — o usuário preferiu um **índice/atalho no topo da tela**, mais robusto a qualquer volume de dados futuro.
- **Granularidade do índice:** drill-down por valor específico — o índice não só pula pro início do bloco de Gênero/Ano, mas deixa escolher o valor exato (ex: "RPG", "1994") e pula direto pra aquela linha.
- **Layout do índice (validado com mockup no navegador):** barra de chips fixa no topo (Meus jogos / Faltam / Gênero / Ano). Chips "Meus jogos"/"Faltam" pulam direto (linha única, sem submenu). Chips "Gênero"/"Ano" expandem um **dropdown inline** (empurra o conteúdo pra baixo, sem modal) listando os valores daquele tipo; tocar num valor rola até a linha exata e fecha o dropdown. Alternativa de bottom sheet (Material3 `ModalBottomSheet`) foi comparada visualmente e descartada — dropdown inline é mais simples de implementar (só estado do Compose) e suficiente pro volume de valores.
- Ordem das linhas na tela **não muda**: continua Meus jogos → Faltam → Gêneros (A-Z + Sem gênero) → Anos (cronológico + Sem ano), do spec anterior. O índice resolve o acesso sem precisar reordenar.

## Arquitetura

```
JogoRepository.observarBiblioteca(): Flow<List<JogoComPosse>>   (inalterado)
        │
        ▼
montarCarrosseis(jogos): List<LinhaCarrossel>   (Task 1 anterior, ganha campo `tipo`)
        │
        ├── BibliotecaViewModel → TelaBiblioteca
        │       │  chip bar no topo (scroll-to via LazyListState)
        │       │  cada linha corta em 20 + card "Ver tudo" se sobrar
        │       ▼
        │   navController.navigate("categoria/${Uri.encode(linha.titulo)}")
        │
        └── TelaCategoriaCompleta (nova)
                │  reobserva observarBiblioteca(), roda montarCarrosseis() de novo,
                │  acha a LinhaCarrossel com titulo == argumento da rota
                ▼
            grid de 3 colunas (reaproveita o card antigo), filtrado pra aquela linha
```

`JogoRepository` e o schema Room não mudam — mesma decisão da spec anterior.

## Modelo de dados

```kotlin
enum class TipoCategoria { MEUS_JOGOS, FALTAM, GENERO, ANO }

data class LinhaCarrossel(
    val titulo: String,
    val jogos: List<JogoComPosse>,
    val tipo: TipoCategoria,
)
```

`montarCarrosseis()` marca cada linha com seu tipo na hora de montar (extensão do que já existe, não reescrita).

## Componentes afetados

- **Modificar**: `MontadorCarrosseisBiblioteca.kt` — `LinhaCarrossel` ganha `tipo`; `montarCarrosseis()` passa o tipo certo em cada `LinhaCarrossel(...)` que já cria.
- **Novo**: função pura `mostrarVerTudo(linha: LinhaCarrossel, cap: Int = 20): Boolean` e `jogosVisiveis(linha: LinhaCarrossel, cap: Int = 20): List<JogoComPosse>` — mesmo arquivo ou vizinho, mesmo padrão de função pura testável do resto do módulo.
- **Modificar**: `TelaBiblioteca.kt` — barra de chips fixa no topo (abaixo da `TopAppBar`), estado do dropdown (`GENERO`/`ANO` expandido ou não), `LazyListState` + `rememberCoroutineScope` pra `animateScrollToItem`; cada `LinhaCarrosselView` corta em `jogosVisiveis()` e adiciona card "Ver tudo" no fim do `LazyRow` quando `mostrarVerTudo()` é true.
- **Novo**: `TelaCategoriaCompleta.kt` (`ui/biblioteca/` ou pacote próprio) + ViewModel correspondente — recebe o título da categoria via argumento de rota, reobserva a biblioteca, filtra pra aquela linha, renderiza grid de 3 colunas (mesmo `CartaoJogo`).
- **Modificar**: `CatalogoNavHost.kt` — nova rota `categoria/{titulo}` (argumento String, `Uri.encode`/`Uri.decode` pra tratar espaços e acentos em nomes de gênero).

## Testes

Teste unitário puro (sem Room/Compose, mesmo padrão de `MontadorCarrosseisBibliotecaTest`), cobrindo:
- Cada `LinhaCarrossel` sai com o `tipo` correto (Meus jogos → `MEUS_JOGOS`, Faltam → `FALTAM`, cada gênero → `GENERO`, cada ano → `ANO`, "Sem gênero"/"Sem ano" → `GENERO`/`ANO` também).
- `mostrarVerTudo()`: false com 20 ou menos jogos, true com 21+.
- `jogosVisiveis()`: retorna a lista inteira quando ≤ 20; corta nos primeiros 20 quando é maior.
