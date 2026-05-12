CREATE TABLE IF NOT EXISTS "MonthlyPlayerSubstitutions" (
  id SERIAL PRIMARY KEY,
  organization_id INTEGER NOT NULL,
  permanent_player_id INTEGER NOT NULL,
  temporary_player_id INTEGER NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE,
  FOREIGN KEY (permanent_player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE,
  FOREIGN KEY (temporary_player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE
);
--;;
CREATE INDEX IF NOT EXISTS monthlysubst_index_org ON "MonthlyPlayerSubstitutions" (organization_id);
