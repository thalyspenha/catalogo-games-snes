# Grid único na home + menu lateral de filtro

Data: 2026-08-03

## Contexto

A `TelaBiblioteca` hoje abre com carrosséis por categoria (Meus jogos, Quero ter,
Faltam, Gênero, Ano — montados por `MontadorCarrosseisBiblioteca.montarCarrosseis()`),
mais uma `BarraDeIndice` fixa no topo com chips de atalho. O usuário achou que:

- A barra de filtro fixa ocupa espaço sempre visível; deveria ser invocada sob demanda.
- A tela inicial deveria sempre mostrar o catálogo completo (1763 jogos), não uma
  categoria específica nem depender de filtro.
- Os cards de jogo têm altura inconsistente: o nome do jogo quebra em 1 ou 2 linhas
  dependendo do tamanho, esticando alguns cards mais que outros.

## Escopo

Substituir carrosséis + barra de índice fixa por: grid único (todos os jogos) como
tela inicial, com um menu lateral (drawer) invocado por clique para filtrar esse
grid. Padronizar altura dos cards. Grid passa de 3 para 4 colunas, com espaçamento
menor entre cards.

Fora de escopo: filtro combinável (status + gênero + ano simultâneos) — fica só um
filtro ativo por vez. Busca por nome (já existente) não muda de comportamento.

## Arquitetura

`TelaBiblioteca` passa a ser um `ModalNavigationDrawer` envolvendo o `Scaffold`
atual. `TopAppBar` ganha um ícone de hambúrguer à esquerda (`navigationIcon`) que
abre o drawer; o título mostra o nome do filtro ativo (ex: "Ação", "Tenho", "2023")
ou "Catálogo SNES" quando o filtro é `Todos`. Os ícones de busca (lupa) e
sincronizar continuam à direita, sem mudança de comportamento.

O corpo da tela é sempre `GridDeJogos` (nunca mais carrossel), mostrando a lista já
filtrada. Quando a busca por nome está expandida, o grid mostra o resultado da
busca no lugar do filtro ativo (busca ignora o filtro — pesquisa sempre nos 1763,
igual ao comportamento atual).

Código removido por ficar sem uso: `MontadorCarrosseisBiblioteca.kt` (incluindo
`LinhaCarrossel`, `TipoCategoria`, `montarCarrosseis()`, `mostrarVerTudo()`,
`jogosVisiveis()`), `TelaCategoriaCompleta.kt` (`CategoriaCompletaViewModel`
incluso), rota de navegação `"categoria/{titulo}"` e o parâmetro
`aoClicarVerTudo` em `CatalogoNavHost`/`TelaBiblioteca`, `BarraDeIndice`,
`LinhaCarrosselView`, `CartaoVerTudo`. Os 28 testes de
`MontadorCarrosseisBibliotecaTest` (que só cobriam a lógica removida) também saem.

## Componentes

### `FiltroBiblioteca` (novo)

Sealed class/enum representando o filtro ativo:

```kotlin
sealed class FiltroBiblioteca {
    object Todos : FiltroBiblioteca()
    object Tenho : FiltroBiblioteca()
    object QueroTer : FiltroBiblioteca()
    object Faltam : FiltroBiblioteca()
    data class Genero(val valor: String) : FiltroBiblioteca() // valor pode ser "Sem gênero"
    data class Ano(val valor: String) : FiltroBiblioteca()    // valor pode ser "Sem ano"
}
```

### `filtrarBiblioteca(jogos: List<JogoComPosse>, filtro: FiltroBiblioteca): List<JogoComPosse>` (novo)

Função pura, substitui a lógica de agrupamento de `montarCarrosseis()`. Regras:

- `Todos` → lista inteira, sem alteração.
- `Tenho` → `status == StatusPosse.TENHO`.
- `QueroTer` → `status == StatusPosse.QUERO_TER`.
- `Faltam` → `status != StatusPosse.TENHO && status != StatusPosse.NAO_INTERESSA`
  (inclui `QUERO_TER` e posse nula — duplicação com `QueroTer` é proposital, mesma
  semântica que já existia nos carrosséis).
- `Genero(valor)` → `jogo.genero == valor`, exceto quando `valor == "Sem gênero"`,
  que casa `jogo.genero == null`.
- `Ano(valor)` → mesma lógica, com `"Sem ano"` casando `jogo.ano == null`.

Testado com JUnit, um caso por ramo (incluindo Sem gênero/Sem ano e a duplicação
Faltam/QueroTer), substituindo os testes deletados de carrossel.

### Listas de valores disponíveis (novo, na ViewModel)

Gêneros e anos distintos presentes no catálogo atual (derivados da lista completa
de jogos a cada emissão do Room, sempre refletindo o estado real — sem opção
"morta" no submenu apontando pra um valor que não existe mais). Cada lista vem
ordenada (gênero alfabético, ano cronológico) e termina com "Sem gênero"/"Sem ano"
se houver pelo menos um jogo sem esse dado.

### Card único (`CartaoJogo`, unifica `CartaoJogo` + `CartaoJogoGrid` de hoje)

Com a remoção de `TelaCategoriaCompleta`, os dois componentes quase-duplicados
viram um só, usado por `GridDeJogos` (home + resultado de busca). O texto do nome
ganha `minLines = 2` (Compose 1.3+, já disponível na BOM do projeto) — reserva
sempre a altura de 2 linhas, nome curto ou longo, cortando com reticências se
passar disso. Isso padroniza a altura do card independente do tamanho do nome.

### `GridDeJogos` (ajustado)

`GridCells.Fixed(4)` em vez de 3. Espaçamento entre cards reduzido via
`horizontalArrangement/verticalArrangement = Arrangement.spacedBy(4.dp)` no
`LazyVerticalGrid`, substituindo o `Modifier.padding(8.dp)` por card atual (que
gerava ~16dp de gap entre cards vizinhos).

### Menu lateral (`ModalNavigationDrawer`, novo)

Lista, em ordem: Todos, Tenho, Quero ter, Faltam, Gênero (expande submenu com a
lista de gêneros + "Sem gênero"), Ano (expande submenu com a lista de anos + "Sem
ano"). Seleção de qualquer item (top-level ou dentro do submenu) fecha o drawer e
aplica o filtro imediatamente — sem estado intermediário de "confirmar".

## Fluxo de dados

`BibliotecaViewModel`:

- Observa `JogoRepository.observarBiblioteca()` diretamente (lista completa, live)
  — não passa mais por `montarCarrosseis()`.
- Novo `filtroSelecionado: StateFlow<FiltroBiblioteca>` (default `Todos`),
  atualizado por uma função nova `aoSelecionarFiltro(filtro: FiltroBiblioteca)`.
- `estadoUi` combina lista completa + `filtroSelecionado` → `jogosFiltrados` (via
  `filtrarBiblioteca`), mais `generosDisponiveis`/`anosDisponiveis` derivados da
  lista completa, mais `carregando` (igual hoje, true até a primeira emissão do
  Room).
- `consultaBusca`/`resultadoBusca` continuam como estão hoje (StateFlow separado,
  ignora o filtro ativo, sempre pesquisa nos 1763).

Tela inicial (filtro `Todos`, sem busca ativa) sempre mostra os 1763 jogos —
satisfaz o requisito de abrir sempre com o catálogo completo, independente de
qualquer filtro anterior (filtro não é persistido entre aberturas do app; cada
abertura começa em `Todos`).

## Tratamento de erro

Nenhum caminho de erro novo: filtragem é operação pura em memória sobre uma lista
já carregada. Se um filtro `Genero(valor)`/`Ano(valor)` ativo deixar de ter jogos
correspondentes (ex: usuário resincroniza e aquele valor some), o grid
simplesmente mostra vazio com a mensagem já existente ("Nenhum jogo encontrado"),
sem crash — mesmo padrão do estado vazio de `GridDeJogos` hoje.

## Teste

- `filtrarBiblioteca`: JUnit, um teste por ramo do `FiltroBiblioteca` (Todos, Tenho,
  QueroTer, Faltam incluindo a duplicação com QueroTer, Genero com valor real,
  Genero "Sem gênero", Ano com valor real, Ano "Sem ano").
- Card/grid (altura padronizada, 4 colunas, espaçamento): verificação visual manual
  no Samsung Galaxy S25 físico, padrão já seguido pelo projeto pra mudança de UI.
