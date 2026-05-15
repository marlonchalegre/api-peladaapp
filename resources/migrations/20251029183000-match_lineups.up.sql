CREATE TABLE IF NOT EXISTS "matchlineups" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  match_id UUID NOT NULL,
  team_id UUID NOT NULL,
  player_id UUID NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  is_goalkeeper BOOLEAN DEFAULT FALSE,
  UNIQUE(match_id, player_id),
  UNIQUE(match_id, team_id, player_id),
  FOREIGN KEY(match_id) REFERENCES "Matches"(id) ON DELETE CASCADE,
  FOREIGN KEY(team_id) REFERENCES "Teams"(id) ON DELETE CASCADE
);