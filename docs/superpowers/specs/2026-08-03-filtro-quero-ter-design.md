# Filtro "Quero ter" na biblioteca — design

## Contexto

A `TelaBiblioteca` já agrupa a biblioteca em carrosséis por categoria via `montarCarrosseis()`
(`MontadorCarrosseisBiblioteca.kt`): "Meus jogos" (status TENHO), "Faltam" (tudo que não é
TENHO nem NAO_INTERESSA — ou seja, hoje mistura QUERO_TER com jogos sem status nenhum),
Gêneros e Anos. Uma barra de chips no topo (`BarraDeIndice`) permite pular direto pra
qualquer linha.

Falta uma forma de ver só os jogos marcados como "Quero ter" separadamente de "Faltam".

## Objetivo

Adicionar uma linha de carrossel dedicada "Quero ter" (status = QUERO_TER), com chip próprio
na barra de índice, seguindo o mesmo padrão já usado por "Meus jogos".

## Decisões

- **Novo carrossel dedicado**, não substitui nem altera "Faltam". Um jogo QUERO_TER vai
  aparecer tanto em "Quero ter" quanto em "Faltam" — duplicação aceita conscientemente, é
  mais simples que reestruturar "Faltam" e não muda nenhum comportamento já existente.
- **Ordem:** Meus jogos → **Quero ter** → Faltam → Gêneros → Anos. Agrupa as categorias de
  status pessoal (Meus jogos/Quero ter) no início, antes de Faltam.
- **Fora de escopo:** nenhum filtro/carrossel para NAO_INTERESSA (não foi pedido); nenhuma
  mudança em `TelaCategoriaCompleta` (tela de "Ver tudo") — ela já reaproveita `LinhaCarrossel`
  genericamente por `tipo`, não precisa de ajuste específico.

## Mudanças

### `MontadorCarrosseisBiblioteca.kt`

- `TipoCategoria` ganha o valor `QUERO_TER`. Mudança aditiva: confirmado por grep que não
  existe nenhum `when` exaustivo sobre esse enum em produção nem em teste (tudo usa
  comparação `==`), então adicionar um valor novo não quebra compilação em nenhum outro
  arquivo.
- `montarCarrosseis()` ganha um novo filtro, inserido entre o bloco de "Meus jogos" e o de
  "Faltam":
  ```kotlin
  val queroTer = jogos.filter { it.posse?.status == StatusPosse.QUERO_TER }
  if (queroTer.isNotEmpty()) {
      linhas += LinhaCarrossel(TITULO_QUERO_TER, queroTer, TipoCategoria.QUERO_TER)
  }
  ```
  Nova constante `TITULO_QUERO_TER = "Quero ter"`. Mesma regra das outras linhas: categoria
  vazia não gera linha.
- O bloco de "Faltam" (filtro e lógica) não muda.

### `TelaBiblioteca.kt` (`BarraDeIndice`)

- Novo chip "Quero ter" entre os chips "Meus jogos" e "Faltam", mesmo padrão do chip
  "Faltam": `enabled` calculado por `linhas.any { it.tipo == TipoCategoria.QUERO_TER }`,
  `onClick` chama `rolarParaPrimeiraDoTipo(TipoCategoria.QUERO_TER)`.

## Testes

Estende `MontadorCarrosseisBibliotecaTest.kt`:
- Linha "Quero ter" aparece contendo só jogos com status QUERO_TER (não TENHO, não
  NAO_INTERESSA, não sem-status).
- Não gera linha "Quero ter" quando não há nenhum jogo com esse status.
- Ordem das linhas: Meus jogos → Quero ter → Faltam → Gêneros → Anos (quando todas presentes).
- `tipo` da linha é `TipoCategoria.QUERO_TER`.

Nenhum teste de UI automatizado existe hoje pra `BarraDeIndice`/`TelaBiblioteca` (mesmo padrão
das features anteriores) — verificação do chip novo fica pra checagem manual no S25 físico,
como já é praxe neste projeto.
