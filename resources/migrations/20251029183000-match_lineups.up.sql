CREATE TABLE IF NOT EXISTS "matchlineups" (
  id SERIAL PRIMARY KEY,
  match_id INTEGER NOT NULL,
  team_id INTEGER NOT NULL,
  player_id INTEGER NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  is_goalkeeper BOOLEAN DEFAULT FALSE,
  UNIQUE(match_id, player_id),
  UNIQUE(match_id, team_id, player_id),
  FOREIGN KEY(match_id) REFERENCES "Matches"(id) ON DELETE CASCADE,
  FOREIGN KEY(team_id) REFERENCES "Teams"(id) ON DELETE CASCADE
);