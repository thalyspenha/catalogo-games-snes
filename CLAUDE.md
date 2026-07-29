# Catálogo de Jogos SNES

App Android para catalogar jogos de Super Nintendo (SNES), feito para o hobby do usuário com games retro/repro. Uso pessoal, alvo de teste principal: Samsung Galaxy S25.

## Idioma

Toda comunicação sobre este projeto deve ser em português do Brasil (pt-br).

## Escopo funcional

- Biblioteca completa dos jogos de SNES (todos os lançados, não só os do usuário), com capa e descrição de cada jogo.
- UI estilo Netflix: grid/carrosséis navegáveis por categoria (gênero, ano, "meus jogos", "faltam").
- Usuário marca quais jogos já possui, com status desde o início (não é um check simples):
  - Tenho / Quero ter / Não me interessa
  - Completude (CIB: cartucho, caixa, manual)
  - Foto da própria cópia
  - Nota de condição
- Dados salvos localmente (Room/SQLite) para uso offline (ex: em lojas/feiras de repro sem sinal).

## Fonte de dados

**ScreenScraper.fr** — escolhida por ser a referência da comunidade retro/repro, com boa cobertura de SNES e capas em várias versões regionais (US/EU/JP).

Estratégia: baixar os dados uma vez (batch) e persistir localmente, em vez de bater na API a cada uso — evita depender de internet no dia a dia do app.

## Stack (proposta, a confirmar durante a implementação)

- Kotlin + Jetpack Compose
- Room (persistência local)
- Retrofit (chamadas à API do ScreenScraper)
- Coil (carregamento/cache de imagens de capa)

## Ambientes de desenvolvimento

O projeto é desenvolvido em dois ambientes:
- macOS (M1) — este Mac
- Linux — ambiente principal, com Android Studio completo

Por isso o contexto do projeto fica registrado aqui neste `CLAUDE.md` (versionado no git), em vez de depender de memória local do Claude Code, que não é compartilhada entre máquinas.

## Status atual

Projeto em fase de definição de escopo, ainda sem código-fonte. Próximos passos: estruturar o projeto Android (Gradle/Compose) e definir o modelo de dados considerando os status extras de posse do jogo.
