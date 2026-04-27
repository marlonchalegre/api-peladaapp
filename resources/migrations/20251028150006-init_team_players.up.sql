CREATE TABLE IF NOT EXISTS "TeamPlayers" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "team_id" INTEGER NOT NULL, "player_id" INTEGER NOT NULL, FOREIGN KEY ("team_id") REFERENCES "Teams"("id"), FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id"));
--;;
CREATE INDEX IF NOT EXISTS "TeamPlayers_index_team" ON "TeamPlayers" ("team_id");
--;;
CREATE INDEX IF NOT EXISTS "TeamPlayers_index_player" ON "TeamPlayers" ("player_id");
