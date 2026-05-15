# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Added
- **Migração para PostgreSQL**
  - O banco de dados principal foi migrado de SQLite para PostgreSQL.
  - Adicionado suporte a HoneySQL para consultas dinâmicas.
  - Nova estrutura de migrations compatível com PostgreSQL.
- **Recurso de Sair da Organização**
  - Jogadores agora podem voluntariamente sair de uma organização.
  - Salvaguarda para impedir que o último administrador saia da organização.
  - Endpoint `POST /api/organizations/:id/leave`.
  - Novos testes de integração para o fluxo de saída.
- **Testes End-to-End (E2E)**
  - Suite de testes completa usando Playwright.
  - Scripts `npm run test:e2e` e `npm run test` com suporte a execução de testes específicos e gravação de vídeo.

### Removed
- **Suporte a Turso/LibSQL e SQLite**
  - Removido driver LibSQL e SQLite.
  - Remoção de lógica de backup SQLite e replicação Litestream.
  - Removidas todas as referências a Turso, LibSQL e SQLite do código-fonte e configurações.

### Changed
- **Otimização de Segurança e Performance (Dashboard)**
  - O endpoint `GET /api/peladas/:id/dashboard-data` agora retorna estritamente os usuários referenciados no contexto (por comparecimento, organizações, eventos, etc.).
  - Remoção de campos com dados sensíveis (PII, como email e telefone) da resposta do dashboard, prevenindo vazamentos e aplicando o princípio do menor privilégio.
  - Otimização do front-end com lazy-loading dos dados financeiros da organização utilizando novo hook `useOrganizationFinance`, eliminando chamadas desnecessárias na página de partidas.
- **Otimização de Docker**
  - builds otimizados com BuildKit e cache de dependências npm.
  - Redução do tempo de build e verificação de dependências em runtime.
- **Melhorias na Interface (Tablet)**
  - Correção de comportamento de clique em listas de organizações para dispositivos touch.
  - Botão "Gerenciar" agora redireciona diretamente para a página de gestão.

### Fixed
- Limpeza de logs de console em ambiente de produção.
- Melhoria na estabilidade das migrations automatizadas.

## [0.3.0] - 2025-01-29
### Added
- **Sistema de Administradores de Organiza??o**
  - Nova tabela `organization_admins` para gerenciar m?ltiplos administradores por organiza??o
  - Endpoints para adicionar e remover administradores
  - Verifica??o de permiss?es para opera??es administrativas
  - Migration `20251029200000-organization_admins.up.sql`
  
- **Sistema de Vota??o e Scores Normalizados**
  - Endpoint de vota??o em lote (`POST /api/votes/batch`)
  - C?lculo de scores normalizados (escala 1-10)
  - Endpoint para consultar informa??es de vota??o (`GET /api/peladas/:id/voting-info`)
  - Valida??es de voto (n?o votar em si mesmo, apenas jogadores eleg?veis)
  - Regras de vota??o: apenas ap?s pelada fechada e para jogadores que participaram
  
- **Melhorias em Peladas**
  - Campo `closed_at` para rastrear quando pelada foi fechada
  - Valida??o de transi??o de status (open ? closed)
  - Prote??o contra reabertura de peladas fechadas
  - Migration `20251029210000-add_closed_at_to_peladas.up.sql`

- **Gerenciamento de Perfil de Usu?rio**
  - Endpoint `PUT /api/user/:id` para atualizar perfil
  - Valida??o de permiss?es (usu?rio s? pode atualizar pr?prio perfil)
  - Suporte para atualiza??o de nome, email e senha
  - Testes de seguran?a para prote??o de perfil

- **Scripts Utilit?rios**
  - `create_scifi_users.sql` - Cria??o de usu?rios de teste tem?ticos
  - `fix_closed_peladas.sql` - Corre??o de status de peladas

### Changed
- **Autoriza??o e Seguran?a**
  - Refatorado sistema de autoriza??o com namespace `logic/authorization`
  - Verifica??o consistente de permiss?es de admin em todos os endpoints
  - Prote??o de opera??es sens?veis (criar pelada, gerenciar times, etc.)
  - Valida??o de que usu?rio pertence ? organiza??o antes de opera??es
  
- **Adaptadores e Modelos**
  - Adicionado campo `is_admin` em responses de usu?rio
  - Melhorias nos adaptadores de usu?rio e credenciais
  - Valida??es mais robustas em controllers

- **Testes**
  - Novos testes de integra??o para admins
  - Testes de prote??o de endpoints
  - Testes de perfil de usu?rio
  - Testes unit?rios de autoriza??o
  - Testes de l?gica de vota??o
  - Cobertura ampliada para cen?rios de seguran?a

### Fixed
- Corre??o de valida??es de transi??o de status de peladas
- Preven??o de vota??o em si mesmo
- Valida??o de jogadores eleg?veis para vota??o
- Prote??o contra cria??o de peladas por n?o-admins
- Corre??o de c?lculo de scores normalizados

### Security
- Implementa??o de verifica??es de autoriza??o em camada
- Prote??o de endpoints administrativos
- Valida??o de propriedade de recursos (usu?rio s? edita pr?prio perfil)
- Verifica??o de pertencimento a organiza??o antes de opera??es

## [0.2.0] - 2025-01-28
### Added
- Sistema completo de partidas e cronograma round-robin
- Substitui??es de jogadores durante partidas
- Estat?sticas de jogadores (gols, assist?ncias, gols contra)
- Eventos de partida (goal, assist, own_goal)

### Changed
- Melhorias na gera??o de cronograma de partidas
- Restri??es de jogos consecutivos e descanso

## [0.1.1] - 2025-01-27
### Changed
- Documenta??o melhorada
- Estrutura de projeto refinada

### Fixed
- Corre??es de estabilidade em produ??o

## [0.1.0] - 2025-01-27
### Added
- Estrutura inicial do projeto
- Autentica??o JWT com Buddy
- CRUD de usu?rios e organiza??es
- Sistema b?sico de peladas e times
- Banco de dados com migrations
- Testes unit?rios e de integra??o
- Dockeriza??o da aplica??o

[Unreleased]: https://github.com/marlon-chalegre/api-peladaapp/compare/0.3.0...HEAD
[0.3.0]: https://github.com/marlon-chalegre/api-peladaapp/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/marlon-chalegre/api-peladaapp/compare/0.1.1...0.2.0
[0.1.1]: https://github.com/marlon-chalegre/api-peladaapp/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/marlon-chalegre/api-peladaapp/releases/tag/0.1.0
