# Filtros e busca na biblioteca

Data: 2026-07-31
Status: aprovado, aguardando plano de implementação

## Contexto

A revisão final do índice de navegação (`docs/superpowers/specs/2026-07-30-indice-navegacao-biblioteca-design.md`) achou 10 findings Minor parked como follow-up. O mais relevante: o card "Ver tudo" da linha **Faltam** abre um grid de ~1700 itens sem nenhuma forma de busca — inviável de usar pra achar um jogo específico. Esta spec fecha esse gap.

Verificado no S25 físico (2026-07-31) antes de desenhar esta spec: a tela de biblioteca renderiza corretamente com dados reais do ScreenScraper (sync de teste com 5 jogos, capas carregando via Coil, sem crash).

## Decisões tomadas com o usuário

- **Escopo**: busca global, acessível da tela de biblioteca principal, sobre o catálogo inteiro (~1763 jogos) — não busca só dentro de uma categoria já aberta.
- **Campo de busca**: só `nome` do jogo. Substring, case-insensitive, sem accent-folding (YAGNI — ajusta depois se virar problema real).
- **Sem filtros extras** (completude/CIB, combinar busca+categoria) — os chips que já existem (Meus jogos/Faltam/Gênero/Ano) já cobrem filtro de categoria; escopo fechado só na busca por nome.
- **Comportamento ao digitar**: substitui os carrosséis por uma lista única de resultado (grid), não filtra dentro de cada carrossel — mais simples de escanear e de implementar.
- **Onde roda o filtro**: em memória, no `BibliotecaViewModel`, sobre a lista que já é observada hoje (`JogoRepository.observarBiblioteca()`). Sem debounce — filtrar 1763 strings por keystroke é instantâneo. Zero mudança em Room/DAO/Repository (rejeitada a alternativa de query SQL `LIKE`: escalaria melhor, mas sem ganho real nesse volume e mais uma camada pra manter, já que `JogoComPosse` é uma relação com join).
- **Entrada na UI**: ícone de lupa na `TopAppBar` (do lado do ícone de sincronizar) que expande um campo de texto no lugar do título — padrão Material comum, tela fica limpa quando não está buscando.
- **Fechar a busca**: ícone "X" limpa a consulta e colapsa o campo de volta (não fica expandido vazio). Consulta em si sobrevive a navegar pro detalhe e voltar (fica no ViewModel), mas reseta se sair da tela de biblioteca e voltar (estado de expansão é local do Compose, não do ViewModel).
- **Reaproveitamento**: o grid de 3 colunas + card usado pra mostrar resultado de busca é o mesmo já usado em `TelaCategoriaCompleta` — extrai pra um composable compartilhado em vez de duplicar.

## Arquitetura

```
JogoRepository.observarBiblioteca(): Flow<List<JogoComPosse>>   (inalterado)
        │
        ▼
combine(jogos, consultaBusca: StateFlow<String>)
        │
        ├── consultaBusca em branco  → linhas = montarCarrosseis(jogos), resultadoBusca = null
        └── consultaBusca não vazia  → resultadoBusca = filtrarPorNome(jogos, consultaBusca)
        │
        ▼
BibliotecaUiState(linhas, resultadoBusca, consultaBusca, carregando)
        │
        ▼
TelaBiblioteca
   resultadoBusca == null → chips + LazyColumn de carrosséis (comportamento atual, inalterado)
   resultadoBusca != null → GridDeJogos(resultadoBusca)  (mesmo grid do "Ver tudo")
```

`JogoRepository` e o schema Room não mudam.

## Modelo de dados

```kotlin
data class BibliotecaUiState(
    val linhas: List<LinhaCarrossel> = emptyList(),
    val resultadoBusca: List<JogoComPosse>? = null, // null = busca fechada/vazia
    val consultaBusca: String = "",
    val carregando: Boolean = true,
)
```

`filtrarPorNome(jogos: List<JogoComPosse>, consulta: String): List<JogoComPosse>` — função pura. Consulta em branco: contrato da função é retornar a lista inteira sem filtrar (a decisão de "não buscar quando vazio" fica no ViewModel, não nela).

## Componentes afetados

- **Modificar**: `MontadorCarrosseisBiblioteca.kt` — nova função pura `filtrarPorNome()`.
- **Modificar**: `BibliotecaViewModel.kt` — `MutableStateFlow<String>` pra consulta + função `aoMudarConsultaBusca(texto: String)`; troca o `.map` atual por `combine` juntando a lista observada com a consulta; `BibliotecaUiState` ganha `resultadoBusca` e `consultaBusca`.
- **Modificar**: `TelaBiblioteca.kt` — segundo `IconButton` (lupa) na `TopAppBar`; estado local `expandido: Boolean`; `TextField` no lugar do título quando expandido, com ícone "X" pra limpar+colapsar; corpo da tela alterna entre carrosséis (atual) e `GridDeJogos(resultadoBusca)` conforme `estado.resultadoBusca`.
- **Novo composable compartilhado**: `GridDeJogos` (extraído de `TelaCategoriaCompleta.kt`, `LazyVerticalGrid(GridCells.Fixed(3))` + card com capa/selo/nome + estado vazio "Nenhum jogo encontrado"), reaproveitado por `TelaCategoriaCompleta` e pelo resultado de busca em `TelaBiblioteca`.
- **Sem mudança**: `JogoRepository`, DAOs, Room, `CatalogoNavHost`.

## Testes

- `filtrarPorNome()` via JUnit (mesmo arquivo de `MontadorCarrosseisBibliotecaTest`): substring no meio do nome, case-insensitive, consulta sem nenhum resultado (lista vazia), consulta em branco/só espaço (retorna lista inteira).
- Sem teste automatizado pra `BibliotecaViewModel`/Compose — segue o padrão já estabelecido no projeto (só lógica pura ganha JUnit; Room/Android/Compose ainda sem infraestrutura de teste instrumentado). Verificação da UI de busca é manual, no S25, mesmo processo usado pros carrosséis.
- Sem caminho de erro novo pra cobrir: filtro é em memória, sem I/O.
