CREATE TABLE IF NOT EXISTS "Peladas" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "organization_id" INTEGER NOT NULL, "scheduled_at" TIMESTAMP, "num_teams" INTEGER, "players_per_team" INTEGER, "status" VARCHAR DEFAULT "open", FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id"));
CREATE INDEX IF NOT EXISTS "Peladas_index_org" ON "Peladas" ("organization_id");
