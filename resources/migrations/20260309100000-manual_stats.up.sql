CREATE TABLE IF NOT EXISTS "ManualStats" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "organization_id" INTEGER NOT NULL,
  "player_id" INTEGER NOT NULL,
  "year" INTEGER NOT NULL,
  "goals" INTEGER DEFAULT 0,
  "assists" INTEGER DEFAULT 0,
  "own_goals" INTEGER DEFAULT 0,
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id") ON DELETE CASCADE,
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id") ON DELETE CASCADE,
  UNIQUE("organization_id", "player_id", "year")
);
--;;
CREATE INDEX IF NOT EXISTS "ManualStats_index_org_year" ON "ManualStats" ("organization_id", "year");
