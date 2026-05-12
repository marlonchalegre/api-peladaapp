CREATE TABLE IF NOT EXISTS "Statistics" (
  id SERIAL PRIMARY KEY,
  created_at TIMESTAMP,
  goals INTEGER,
  own_goal INTEGER,
  assistences INTEGER,
  pelada_id INTEGER,
  organization_player_id INTEGER,
  player_id INTEGER,
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id),
  FOREIGN KEY (organization_player_id) REFERENCES "Organizations"(id),
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id)
);
