CREATE TABLE IF NOT EXISTS "TeamPlayers" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  team_id UUID NOT NULL,
  player_id UUID NOT NULL,
  is_goalkeeper BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (team_id) REFERENCES "Teams"(id),
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id)
);
--;;
CREATE INDEX IF NOT EXISTS teamplayers_index_team ON "TeamPlayers" (team_id);
--;;
CREATE INDEX IF NOT EXISTS teamplayers_index_player ON "TeamPlayers" (player_id);
