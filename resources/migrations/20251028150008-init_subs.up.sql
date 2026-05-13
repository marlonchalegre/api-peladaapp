CREATE TABLE IF NOT EXISTS "MatchSubstitutions" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  match_id UUID NOT NULL,
  minute INTEGER,
  out_player_id UUID NOT NULL,
  in_player_id UUID NOT NULL,
  FOREIGN KEY (match_id) REFERENCES "Matches"(id),
  FOREIGN KEY (out_player_id) REFERENCES "OrganizationPlayers"(id),
  FOREIGN KEY (in_player_id) REFERENCES "OrganizationPlayers"(id)
);
--;;
CREATE INDEX IF NOT EXISTS matchsubstitutions_index_match ON "MatchSubstitutions" (match_id);
--;;
