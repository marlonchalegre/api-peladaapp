CREATE TABLE IF NOT EXISTS "Statistics" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at TIMESTAMP,
  goals INTEGER,
  own_goal INTEGER,
  assistences INTEGER,
  pelada_id UUID,
  organization_player_id UUID,
  player_id UUID,
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id),
  FOREIGN KEY (organization_player_id) REFERENCES "Organizations"(id),
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id)
);
