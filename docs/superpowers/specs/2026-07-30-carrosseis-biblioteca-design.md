# Carrosséis por categoria na biblioteca

Data: 2026-07-30
Status: aprovado, aguardando plano de implementação

## Contexto

A `TelaBiblioteca` hoje mostra um grid único (`LazyVerticalGrid`, 3 colunas) com todos os jogos do catálogo, sem agrupamento. O escopo funcional do produto (CLAUDE.md) sempre previu uma UI estilo Netflix — grid/carrosséis navegáveis por categoria (gênero, ano, "meus jogos", "faltam") — que ainda não tinha sido implementada.

Investigação confirmou que os dados necessários já existem e já estão persistidos, sem gap de coleta:
- `JogoEntity.genero: String?` e `JogoEntity.anoLancamento: Int?` — mapeados do ScreenScraper em `ScreenScraperMapper.kt`, já presentes no catálogo sincronizado.
- `PosseUsuarioEntity.status: StatusPosse` (TENHO / QUERO_TER / NAO_INTERESSA) — já existe via `JogoComPosse.posse`.
- `JogoRepository.observarBiblioteca()` já expõe `Flow<List<JogoComPosse>>` com o catálogo completo (1763 jogos).

## Decisões tomadas com o usuário

- Quatro categorias na primeira versão: **Meus jogos**, **Faltam**, **Gênero**, **Ano** — sem cortar nenhuma pra depois.
- **Meus jogos**: jogos com `posse?.status == TENHO`.
- **Faltam**: jogos com `posse == null` ou `posse.status != TENHO`, **excluindo** `NAO_INTERESSA` (usuário já disse explicitamente que não quer esses; não devem aparecer como pendência).
- **Gênero**: uma linha por valor exato de `JogoEntity.genero` encontrado no catálogo, **dinâmico** (sem lista curada/fixa). Ordenação **alfabética**. Jogos com `genero == null` caem numa linha **"Sem gênero"**, no fim do bloco de gêneros.
- **Ano**: uma linha por valor exato de `JogoEntity.anoLancamento`, **cronológico** (não agrupado por década). Jogos com `anoLancamento == null` caem numa linha **"Sem ano"**, no fim do bloco de anos.
- Ordem das linhas na tela: **Meus jogos → Faltam → Gêneros (A-Z + Sem gênero) → Anos (cronológico + Sem ano)**.
- Categoria sem nenhum jogo não gera linha (não renderiza carrossel vazio).
- Tap num jogo dentro de qualquer carrossel navega pra `detalhe/{jogoId}`, mesma rota/comportamento de hoje — sem mudança de navegação.
- Sem "ver tudo"/expansão de categoria nessa versão (YAGNI) — cada linha é só um `LazyRow` horizontal do card já existente (capa + selo de status).
- Loading/erro geral da tela (estado do `observarBiblioteca()`) não muda — só a apresentação dos dados quando carregados com sucesso.

## Arquitetura

```
JogoRepository.observarBiblioteca(): Flow<List<JogoComPosse>>   (inalterado)
        │
        ▼
MontadorCarrosseisBiblioteca (novo, função/classe pura, sem Room/Compose)
        │  agrupa + ordena List<JogoComPosse> → List<LinhaCarrossel>
        ▼
BibliotecaViewModel (state muda de lista plana pra List<LinhaCarrossel>)
        │
        ▼
TelaBiblioteca: LazyColumn de linhas, cada linha = título + LazyRow de cards
```

`JogoRepository` e o schema Room não mudam — a transformação é só de apresentação, em cima do dado que já é observado hoje.

## Modelo de dados

```kotlin
data class LinhaCarrossel(
    val titulo: String,
    val jogos: List<JogoComPosse>,
)
```

Sem enum de categoria: a ordem das linhas vem da ordem em que `MontadorCarrosseisBiblioteca` monta a lista de saída (Meus jogos, depois Faltam, depois um `groupBy` alfabético de gênero, depois um `groupBy` cronológico de ano), não de um campo de ordenação separado.

## Componentes afetados

- **Novo**: `MontadorCarrosseisBiblioteca` (local sugerido: `ui/biblioteca/`, ao lado do ViewModel) — função pura `fun montar(jogos: List<JogoComPosse>): List<LinhaCarrossel>`.
- **`BibliotecaViewModel`**: `BibliotecaUiState` passa a carregar `List<LinhaCarrossel>` em vez de `List<JogoComPosse>` plana; chama `MontadorCarrosseisBiblioteca` ao mapear o `Flow` do repository.
- **`TelaBiblioteca`**: troca `LazyVerticalGrid` por `LazyColumn` de linhas; cada linha renderiza título + `LazyRow` reaproveitando o composable de card (capa + selo de status) que já existe hoje no grid.

## Testes

Teste unitário puro de `MontadorCarrosseisBiblioteca` (mesmo padrão de `CalculoRestanteTest`, sem Room/Compose/instrumentação), cobrindo:
- Ordem das 4 categorias na saída (Meus jogos, Faltam, Gêneros, Anos, nessa sequência).
- Gêneros em ordem alfabética.
- Anos em ordem cronológica.
- "Sem gênero" e "Sem ano" aparecem por último em seus respectivos blocos.
- `NAO_INTERESSA` não aparece em "Faltam".
- Jogo com `status == TENHO` não aparece em "Faltam" (só em "Meus jogos").
- Categoria sem jogos não gera `LinhaCarrossel` (lista de saída não tem entrada vazia).
