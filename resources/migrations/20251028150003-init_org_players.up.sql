CREATE TABLE IF NOT EXISTS "OrganizationPlayers" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL,
  user_id UUID NOT NULL,
  grade REAL,
  position_id UUID,
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
