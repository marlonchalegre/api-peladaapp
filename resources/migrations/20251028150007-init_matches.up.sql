CREATE TABLE IF NOT EXISTS "Matches" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  pelada_id UUID NOT NULL,
  home_team_id UUID NOT NULL,
  away_team_id UUID NOT NULL,
  sequence INTEGER NOT NULL,
  status VARCHAR DEFAULT 'scheduled',
  home_score INTEGER DEFAULT 0,
  away_score INTEGER DEFAULT 0,
  timer_started_at TIMESTAMP,
  timer_accumulated_ms INTEGER DEFAULT 0,
  timer_status VARCHAR DEFAULT 'stopped' CHECK (timer_status IN ('stopped', 'running', 'paused')),
  UNIQUE (pelada_id, sequence),
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id),
  FOREIGN KEY (home_team_id) REFERENCES "Teams"(id),
  FOREIGN KEY (away_team_id) REFERENCES "Teams"(id)
);
--;;
CREATE INDEX IF NOT EXISTS matches_index_pelada ON "Matches" (pelada_id);
--;;
CREATE INDEX IF NOT EXISTS matches_index_sequence ON "Matches" (pelada_id, sequence);
--;;
CREATE INDEX IF NOT EXISTS matches_index_home_team ON "Matches" (home_team_id);
--;;
CREATE INDEX IF NOT EXISTS matches_index_away_team ON "Matches" (away_team_id);
--;;
