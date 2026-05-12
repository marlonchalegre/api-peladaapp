CREATE TABLE IF NOT EXISTS "OrganizationPlayers" (
  id SERIAL PRIMARY KEY,
  organization_id INTEGER NOT NULL,
  user_id INTEGER NOT NULL,
  grade REAL,
  position_id INTEGER,
  member_type VARCHAR NOT NULL DEFAULT 'diarista',
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id),
  FOREIGN KEY (user_id) REFERENCES "Users"(id),
  FOREIGN KEY (position_id) REFERENCES "Positions"(id)
);
--;;
CREATE INDEX IF NOT EXISTS orgplayers_index_org ON "OrganizationPlayers" (organization_id);
--;;
CREATE INDEX IF NOT EXISTS orgplayers_index_user ON "OrganizationPlayers" (user_id);
--;;
CREATE UNIQUE INDEX IF NOT EXISTS orgplayers_unique_org_user ON "OrganizationPlayers" (organization_id, user_id);
--;;
