# Testes

## Infraestrutura

- JUnit **4.13.2** (`gradle/libs.versions.toml`, alias `libs.junit`), declarado como `testImplementation` em `app/build.gradle.kts` e `ferramentas/build.gradle.kts`.
- Testes do app ficam em `app/src/test/java/com/thalys/catalogosnes/` (testes unitários JVM). Testes das ferramentas ficam em `ferramentas/src/test/kotlin/ferramentas/`.
- Não há `androidTest` (nenhum teste instrumentado).

## Contagem

**28 `@Test`** (contados via grep): 27 em `:app` e 1 em `:ferramentas`. `PossesPendentesTest` (3): normalização de nome (caixa/acento/pontuação e artigo "the") e extração de posses do seed.

## Classes e casos

### `app/.../data/sync/CalculoRestanteTest.kt` (4): `calcularRestante`
- `primeira execucao, sem sucesso gravado, retorna catalogo inteiro`
- `retomada parcial, exclui os ja marcados como sucesso`
- `tudo sincronizado, retorna lista vazia`
- `item com falha registrada nao esta em sucesso, entao continua no restante para nova tentativa`

### `app/.../data/sync/CatalogoMestreLoaderTest.kt` (1)
- `parseia lista de itens do catalogo mestre a partir do JSON`: parsing do JSON do catálogo mestre para DTOs.

### `app/.../data/sync/CotaEsgotadaTest.kt` (3): heurística `cotaEsgotada`
- `detecta erro mencionando quota no header`
- `nao detecta cota quando nao ha erro`
- `nao detecta cota em erro nao relacionado`

### `app/.../ui/biblioteca/FiltroBibliotecaTest.kt` (16)
- `filtro Todos retorna a lista inteira`
- `filtro Tenho retorna so status TENHO`
- `filtro QueroTer retorna so status QUERO_TER`
- `filtro Faltam inclui quero ter e sem posse, exclui tenho e nao interessa`
- `filtro Genero com valor real casa pelo nome exato`
- `filtro Genero Sem genero casa jogos sem genero ou com genero em branco`
- `filtro Ano com valor real casa pelo ano exato`
- `filtro Ano Sem ano casa jogos sem ano cadastrado`
- `generosDisponiveis ordena alfabetico e poe Sem genero no fim`
- `generosDisponiveis sem nenhum jogo sem genero, nao inclui Sem genero`
- `anosDisponiveis ordena cronologico e poe Sem ano no fim`
- `anosDisponiveis sem nenhum jogo sem ano, nao inclui Sem ano`
- `filtrarPorNome acha substring no meio do nome, case-insensitive`
- `filtrarPorNome sem nenhum resultado retorna lista vazia`
- `filtrarPorNome com consulta em branco retorna a lista inteira`
- `filtrarPorNome ignora espaco no inicio e fim da consulta`

### `ferramentas/.../AgrupamentoClonesTest.kt` (1)
- `agrupa por cloneofid, escolhe raiz quando elegivel, exclui beta duplo-categorizado`: testa `agruparEDeduplicar`.

## Como rodar

```
./gradlew :app:testDebugUnitTest      # ou ./gradlew test (roda :app e :ferramentas)
./gradlew :ferramentas:test
```
O JAVA_HOME precisa estar configurado. No Mac, use o JBR do Android Studio; no NixOS, um JDK 21 do `/nix/store` (ver CLAUDE.md).

## Sem cobertura

- Room: DAOs, migrações 1→2 e 2→3, seed via `onCreate`.
- `SincronizacaoRepository` como fluxo (retry, disjuntor, limpeza inicial, cancelamento). Só as funções puras auxiliares têm teste.
- `ScreenScraperMapper` (prioridade de região/idioma, escolha de capa, extração de ano).
- `CapaDownloader`, `NetworkModule` e a rede em geral.
- ViewModels (`BibliotecaViewModel`, `DetalheJogoViewModel`, `SincronizacaoViewModel`).
- UI Compose, navegação e qualquer teste instrumentado.
- `parsearDat` (XML) em `:ferramentas`.

A validação de UI e integração foi feita manualmente no Galaxy S25 (ver `docs/history.md`).
