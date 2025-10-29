-- Users
CREATE TABLE IF NOT EXISTS "Users" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "email" VARCHAR UNIQUE,
  "password" VARCHAR,
  "name" VARCHAR
);
CREATE INDEX IF NOT EXISTS "Users_index_email" ON "Users" ("email");

-- Organizations
CREATE TABLE IF NOT EXISTS "Organizations" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "name" VARCHAR
);

-- Positions (optional lookup)
CREATE TABLE IF NOT EXISTS "Positions" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "value" VARCHAR
);

-- OrganizationPlayers (players are users within an organization)
CREATE TABLE IF NOT EXISTS "OrganizationPlayers" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "organization_id" INTEGER NOT NULL,
  "user_id" INTEGER NOT NULL,
  "grade" REAL,
  "position_id" INTEGER,
  FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id"),
  FOREIGN KEY ("user_id") REFERENCES "Users"("id"),
  FOREIGN KEY ("position_id") REFERENCES "Positions"("id")
);
CREATE INDEX IF NOT EXISTS "OrgPlayers_index_org" ON "OrganizationPlayers" ("organization_id");
CREATE INDEX IF NOT EXISTS "OrgPlayers_index_user" ON "OrganizationPlayers" ("user_id");

-- Peladas (game days)
CREATE TABLE IF NOT EXISTS "Peladas" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "organization_id" INTEGER NOT NULL,
  "scheduled_at" TIMESTAMP,
  "num_teams" INTEGER,
  "players_per_team" INTEGER,
  "status" VARCHAR DEFAULT 'open',
  FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id")
);
CREATE INDEX IF NOT EXISTS "Peladas_index_org" ON "Peladas" ("organization_id");

-- Teams (per pelada)
CREATE TABLE IF NOT EXISTS "Teams" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "pelada_id" INTEGER NOT NULL,
  "name" VARCHAR,
  FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id")
);
CREATE INDEX IF NOT EXISTS "Teams_index_pelada" ON "Teams" ("pelada_id");

-- TeamPlayers (bridge table)
CREATE TABLE IF NOT EXISTS "TeamPlayers" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "team_id" INTEGER NOT NULL,
  "player_id" INTEGER NOT NULL, -- OrganizationPlayers.id
  FOREIGN KEY ("team_id") REFERENCES "Teams"("id"),
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id")
);
CREATE INDEX IF NOT EXISTS "TeamPlayers_index_team" ON "TeamPlayers" ("team_id");
CREATE INDEX IF NOT EXISTS "TeamPlayers_index_player" ON "TeamPlayers" ("player_id");

-- Matches
CREATE TABLE IF NOT EXISTS "Matches" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "pelada_id" INTEGER NOT NULL,
  "home_team_id" INTEGER NOT NULL,
  "away_team_id" INTEGER NOT NULL,
  "sequence" INTEGER NOT NULL,
  "status" VARCHAR DEFAULT 'scheduled',
  "home_score" INTEGER DEFAULT 0,
  "away_score" INTEGER DEFAULT 0,
  UNIQUE ("pelada_id", "sequence"),
  FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id"),
  FOREIGN KEY ("home_team_id") REFERENCES "Teams"("id"),
  FOREIGN KEY ("away_team_id") REFERENCES "Teams"("id")
);
CREATE INDEX IF NOT EXISTS "Matches_index_pelada" ON "Matches" ("pelada_id");
CREATE INDEX IF NOT EXISTS "Matches_index_sequence" ON "Matches" ("pelada_id", "sequence");

-- Match Substitutions
CREATE TABLE IF NOT EXISTS "MatchSubstitutions" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "match_id" INTEGER NOT NULL,
  "minute" INTEGER,
  "out_player_id" INTEGER NOT NULL,
  "in_player_id" INTEGER NOT NULL,
  FOREIGN KEY ("match_id") REFERENCES "Matches"("id"),
  FOREIGN KEY ("out_player_id") REFERENCES "OrganizationPlayers"("id"),
  FOREIGN KEY ("in_player_id") REFERENCES "OrganizationPlayers"("id")
);
CREATE INDEX IF NOT EXISTS "MatchSubstitutions_index_match" ON "MatchSubstitutions" ("match_id");

-- Statistics (optional, kept for future)
CREATE TABLE IF NOT EXISTS "Statistics" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "created_at" TIMESTAMP,
  "goals" INTEGER,
  "own_goal" INTEGER,
  "assistences" INTEGER,
  "pelada_id" INTEGER,
  "organization_player_id" INTEGER,
  "player_id" INTEGER,
  FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id"),
  FOREIGN KEY ("organization_player_id") REFERENCES "Organizations"("id"),
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id")
);

-- Votes (1..5 stars per player by other players per pelada)
CREATE TABLE IF NOT EXISTS "Votes" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "pelada_id" INTEGER NOT NULL,
  "voter_id" INTEGER NOT NULL,
  "target_id" INTEGER NOT NULL,
  "stars" INTEGER NOT NULL CHECK ("stars" >= 1 AND "stars" <= 5),
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE ("pelada_id", "voter_id", "target_id"),
  FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id"),
  FOREIGN KEY ("voter_id") REFERENCES "OrganizationPlayers"("id"),
  FOREIGN KEY ("target_id") REFERENCES "OrganizationPlayers"("id")
);
CREATE INDEX IF NOT EXISTS "Votes_index_pelada" ON "Votes" ("pelada_id");
CREATE INDEX IF NOT EXISTS "Votes_index_target" ON "Votes" ("pelada_id", "target_id");
