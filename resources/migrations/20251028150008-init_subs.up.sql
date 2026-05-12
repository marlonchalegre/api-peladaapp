CREATE TABLE IF NOT EXISTS "MatchSubstitutions" (
  id SERIAL PRIMARY KEY,
  match_id INTEGER NOT NULL,
  minute INTEGER,
  out_player_id INTEGER NOT NULL,
  in_player_id INTEGER NOT NULL,
  FOREIGN KEY (match_id) REFERENCES "Matches"(id),
  FOREIGN KEY (out_player_id) REFERENCES "OrganizationPlayers"(id),
  FOREIGN KEY (in_player_id) REFERENCES "OrganizationPlayers"(id)
);
--;;
CREATE INDEX IF NOT EXISTS matchsubstitutions_index_match ON "MatchSubstitutions" (match_id);
--;;
