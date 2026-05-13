CREATE TABLE IF NOT EXISTS peladaattendance (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  pelada_id UUID NOT NULL,
  player_id UUID NOT NULL,
  status VARCHAR NOT NULL CHECK (status IN ('confirmed', 'declined', 'pending', 'waitlist')),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  voting_enabled BOOLEAN DEFAULT TRUE,
  UNIQUE (pelada_id, player_id),
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id),
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id)
);
--;;
CREATE INDEX IF NOT EXISTS attendance_index_pelada ON peladaattendance (pelada_id);