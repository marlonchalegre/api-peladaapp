CREATE TABLE IF NOT EXISTS "Matches" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "pelada_id" INTEGER NOT NULL, "home_team_id" INTEGER NOT NULL, "away_team_id" INTEGER NOT NULL, "sequence" INTEGER NOT NULL, "status" VARCHAR DEFAULT "scheduled", "home_score" INTEGER DEFAULT 0, "away_score" INTEGER DEFAULT 0, UNIQUE ("pelada_id", "sequence"), FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id"), FOREIGN KEY ("home_team_id") REFERENCES "Teams"("id"), FOREIGN KEY ("away_team_id") REFERENCES "Teams"("id"));
--;;
CREATE INDEX IF NOT EXISTS "Matches_index_pelada" ON "Matches" ("pelada_id");
--;;
CREATE INDEX IF NOT EXISTS "Matches_index_sequence" ON "Matches" ("pelada_id", "sequence");
