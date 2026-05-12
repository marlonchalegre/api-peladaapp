CREATE TABLE IF NOT EXISTS "TeamPlayers" (
  id SERIAL PRIMARY KEY,
  team_id INTEGER NOT NULL,
  player_id INTEGER NOT NULL,
  is_goalkeeper BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (team_id) REFERENCES "Teams"(id),
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id)
);
--;;
CREATE INDEX IF NOT EXISTS teamplayers_index_team ON "TeamPlayers" (team_id);
--;;
CREATE INDEX IF NOT EXISTS teamplayers_index_player ON "TeamPlayers" (player_id);
--;;
