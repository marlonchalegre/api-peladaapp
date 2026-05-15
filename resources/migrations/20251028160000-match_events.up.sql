CREATE TABLE IF NOT EXISTS "MatchEvents" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  match_id UUID NOT NULL,
  player_id UUID NOT NULL,
  event_type VARCHAR NOT NULL CHECK (event_type IN ('assist','goal','own_goal')),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  session_time_ms INTEGER,
  match_time_ms INTEGER,
  FOREIGN KEY (match_id) REFERENCES "Matches"(id),
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id)
);
--;;
CREATE INDEX IF NOT EXISTS matchevents_index_match ON "MatchEvents" (match_id);
--;;
CREATE INDEX IF NOT EXISTS matchevents_index_player ON "MatchEvents" (player_id);