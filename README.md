# ⚽️ PeladaApp API

A Clojure HTTP API to organize casual soccer (pelada) with friends: manage users/players, organizations, game days (peladas), teams, round‑robin matches with constraints, substitutions, and post‑game voting with normalized scores. Built on Ring/Compojure, next.jdbc (SQLite), and Buddy Auth.

---

### 📖 Overview
- **Authentication**: Register/login, JWT auth (`Authorization: Token <jwt>`). Only `/auth/register` and `/auth/login` are public.
- **Users/Players**: Users are the system’s identities; players are users inside an organization.
- **Organizations**: CRUD organizations; scope players and peladas per organization.
- **Peladas (Game Days)**: Create, configure (`num_teams`, `players_per_team`), begin (generate schedule), close. No longer requires an even number of teams.
- **Team Randomization**: Randomly assign players to teams based on available slots.
- **Voting & Scores**: 1–5 star votes (no self‑vote), compute normalized scores (1–10). Player scores are visible.
- **JSON everywhere**: All endpoints always return JSON bodies, including errors and deletes.

---

### 🚀 Installation

- Local (Lein):
```bash
# Run tests
lein test

# Start dev REPL (optional)
lein repl

# Run the app (AOT main: api-peladaapp.core)
lein run
```

- Docker:
```bash
# Build
docker build -t api-peladaapp:latest .

# Run (ephemeral DB inside the container)
docker run --rm -p 8080:8080 api-peladaapp:latest

# Run with persistent SQLite DB + custom config
docker run --rm -p 8080:8080 \
  -v "$(pwd)/peladaapp.db:/app/peladaapp.db" \
  -v "$(pwd)/resources/config.json:/app/resources/config.json:ro" \
  api-peladaapp:latest
```

---

### 🛠️ Usage

- Health check (example — adjust to your routes):
```bash
curl -i http://localhost:8080/auth/login -X POST \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"s3cret"}'
```

- Typical flow:
  1. Register → `/auth/register` (POST)
  2. Login → `/auth/login` (POST) → receive `token`
  3. Send `Authorization: Token <token>` for all `/api/**` routes
  4. Create Organization → `/api/organizations` (POST)
  5. Add Players (users into organization) → `/api/players` (POST)
  6. Create Pelada → `/api/peladas` (POST), set config, begin
  7. Create Teams → `/api/teams` (POST), add players to teams
  8. Randomize Teams → `/api/peladas/:id/teams/randomize` (POST)
  9. Begin Pelada → generates Matches → `/api/matches`
  10. Update Match scores → `/api/matches/:id` (PUT)
  11. Substitutions → `/api/substitutions` (POST)
  12. Votes → `/api/votes` (POST); compute normalized scores
  13. Get Normalized Player Scores → `/api/scores/normalized` (POST)

- Auth header example:
```bash
-H "Authorization: Token <jwt>"
```

---

### 📦 Technologies
- **Language/Runtime**: Clojure 1.11, JVM 21
- **Web**: Ring, Compojure
- **Auth**: Buddy (sign, auth, hashers) with HS512 JWT
- **DB**: SQLite (`org.xerial/sqlite-jdbc`), next.jdbc, HikariCP
- **Schemas**: Prismatic Schema
- **Components**: Stuart Sierra Component
- **Algorithms**: clojure.math.combinatorics
- **Testing**: clojure.test, ring-mock
- **Build**: Leiningen, Docker (multi‑stage)

---

### 🔧 Configuration
- Config file: `resources/config.json`
```json
{"jwt-secret": "secret"}
```
- Keys:
  - **jwt-secret**: Symmetric key for JWT signing (HS512).
- DB: SQLite file `peladaapp.db` in working dir; handled by HikariCP via `components.clj`.
- Port: `8080` (see `components.clj`).

Override in Docker by bind mounting updated files into `/app` (see Docker run example above).

---

### ✅ Requirements
- JDK 21+
- Leiningen
- SQLite (embedded via JDBC driver; no external server required)
- Docker (optional, for containerized runs)

---

### 🗂️ Repository Structure
```text
/                      # Project root
├─ project.clj         # Leiningen config (deps, main, test paths, migratus)
├─ pom.xml             # Maven interop (generated/maintained for IDEs if needed)
├─ peladaapp.db        # SQLite DB file (local dev; can be regenerated)
├─ resources/
│  ├─ config.json      # App configuration (JWT secret, etc.)
│  └─ migrations/
│     ├─ 20251028150000-init_all.up.sql  # Consolidated schema for all tables
│     ├─ 20251028160000-match_events.up.sql
│     ├─ 20251029183000-match_lineups.up.sql
│     ├─ 20251029200000-organization_admins.up.sql
│     ├─ 20251029210000-add_closed_at_to_peladas.up.sql
│     └─ 20251029220000-create_player_scores_view.up.sql
├─ scripts/
│  └─ create_anime_users.sql # Script to seed anime users
├─ src/api_peladaapp/
│  ├─ core.clj         # Entry point (-main) starting the Component system
│  ├─ components.clj   # System wiring: DB, App, WebServer (Jetty on :8080)
│  ├─ server.clj       # Ring app stack (middleware) and `app`
│  ├─ routes.clj       # Compojure routes & access rules
│  ├─ config.clj       # Loads config.json
│  ├─ helpers/         # Shared helpers (responses, exceptions, misc, pagination)
│  ├─ models/          # Schema definitions for entities
│  ├─ adapters/        # in→model, db→model, model→out conversions
│  ├─ controllers/     # Business logic per entity (auth, pelada, team, user, organization, score, randomize, etc.)
│  ├─ db/              # next.jdbc data access (CRUD, queries)
│  ├─ handlers/        # HTTP handlers mapping to controllers
│  ├─ logic/           # Scheduling and other core algorithms (pelada, randomize, schedule, score, user, vote)
├─ dev/
│  └─ dev.clj          # REPL support with component.repl
├─ test/
│  ├─ unit/            # Unit tests (pure functions, small scope)
│  ├─ integration/     # Integration tests (end-to-end HTTP flows)
│  └─ api_peladaapp/test_helpers.clj  # Test utilities (DB reset, auth, decode)
├─ Dockerfile          # Multi-stage build (uberjar + slim runtime)
├─ CHANGELOG.md        # Changes over time
├─ LICENSE             # MIT License
└─ README.md           # This file
```

- **Database schema (consolidated)** includes: `Users`, `Organizations`, `Positions`, `OrganizationPlayers`, `Peladas`, `Teams`, `TeamPlayers`, `Matches`, `MatchSubstitutions`, `Statistics`, `Votes`.
- **Access control** via Buddy access rules; only `/auth/register` and `/auth/login` are public.
- **JSON responses** enforced centrally in `helpers/responses.clj`.

---

### 🔗 Flow Chart (Mermaid)
```mermaid
flowchart TD
  subgraph Client
    U[User]
  end

  subgraph API[HTTP API]
    A[Ring/Compojure Routes]
    M[Middleware\nJSON, AuthN/Z, Access Rules]
    H[Handlers]
    C[Controllers]
    D[Adapters]
    S[Schemas]
  end

  subgraph Data[Persistence]
    DB[(SQLite)]
  end

  U -->|HTTP| A --> M --> H --> C --> D --> DB
  C --> S
```

---

### Common Endpoints (high level)
- `POST /auth/register`, `POST /auth/login`
- `GET /api/users` (paginated)
- `GET/PUT/DELETE /api/user/:id`
- `POST/GET /api/organizations` (GET paginated)
- `POST/GET /api/players`
- `POST/GET /api/peladas`, `POST /api/peladas/:id/begin`, `POST /api/peladas/:id/close`, `POST /api/peladas/:id/teams/randomize`
- `POST/GET /api/teams`
- `GET/PUT /api/matches` and `/api/matches/:id`
- `POST /api/matches/:pelada_id/events`
- `DELETE /api/matches/:id/events`
- `POST /api/substitutions`
- `POST/GET /api/votes`
- `POST /api/scores/normalized`

All `/api/**` require `Authorization: Token <jwt>`.

---

### Development Tips
- Clean DB during dev: delete `peladaapp.db` and restart; tests recreate schema directly from the consolidated SQL.
- Test helpers handle JWT auth and tolerant JSON decoding.
- Middleware order is important; see `server.clj` for final working order.

---

### License
MIT License. See `LICENSE`.
