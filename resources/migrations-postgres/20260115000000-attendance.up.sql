CREATE TABLE IF NOT EXISTS peladaattendance (
  id SERIAL PRIMARY KEY,
  pelada_id INTEGER NOT NULL,
  player_id INTEGER NOT NULL,
  status VARCHAR NOT NULL CHECK (status IN ('confirmed', 'declined', 'pending', 'waitlist')),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  voting_enabled BOOLEAN DEFAULT TRUE,
  UNIQUE (pelada_id, player_id),
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id),
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id)
);
--;;
CREATE INDEX IF NOT EXISTS attendance_index_pelada ON peladaattendance (pelada_id);