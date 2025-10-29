-- Create per-match lineup table so substitutions affect only the match
CREATE TABLE IF NOT EXISTS matchlineups (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  match_id INTEGER NOT NULL,
  team_id INTEGER NOT NULL,
  player_id INTEGER NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(match_id, player_id),
  UNIQUE(match_id, team_id, player_id),
  FOREIGN KEY(match_id) REFERENCES Matches(id) ON DELETE CASCADE,
  FOREIGN KEY(team_id) REFERENCES Teams(id) ON DELETE CASCADE
);
