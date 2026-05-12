CREATE TABLE IF NOT EXISTS "Peladas" (
  id SERIAL PRIMARY KEY,
  organization_id INTEGER NOT NULL,
  scheduled_at TIMESTAMP,
  num_teams INTEGER,
  players_per_team INTEGER,
  status VARCHAR DEFAULT 'open',
  closed_at TIMESTAMP,
  fixed_goalkeepers BOOLEAN DEFAULT FALSE,
  home_fixed_goalkeeper_id INTEGER REFERENCES "OrganizationPlayers"(id),
  away_fixed_goalkeeper_id INTEGER REFERENCES "OrganizationPlayers"(id),
  timer_started_at TIMESTAMP,
  timer_accumulated_ms INTEGER DEFAULT 0,
  timer_status VARCHAR DEFAULT 'stopped' CHECK (timer_status IN ('stopped', 'running', 'paused')),
  vote_ended_message_sent BOOLEAN DEFAULT FALSE,
  vote_reminder_12h_sent BOOLEAN DEFAULT FALSE,
  vote_reminder_23h_sent BOOLEAN DEFAULT FALSE,
  last_attendance_reminder_at TIMESTAMP,
  vote_reminder_30m_sent BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id)
);
--;;
CREATE INDEX IF NOT EXISTS peladas_index_org ON "Peladas" (organization_id);
--;;
CREATE INDEX IF NOT EXISTS peladas_org_sched ON "Peladas" (organization_id, scheduled_at);
--;;
CREATE INDEX IF NOT EXISTS peladas_status_closed ON "Peladas" (status, closed_at);
--;;
