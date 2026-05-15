CREATE TABLE IF NOT EXISTS "OrganizationAdmins" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL,
  user_id UUID NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (organization_id, user_id),
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES "Users"(id) ON DELETE CASCADE
);
--;;
CREATE INDEX IF NOT EXISTS orgadmins_index_org ON "OrganizationAdmins" (organization_id);
--;;
CREATE INDEX IF NOT EXISTS orgadmins_index_user ON "OrganizationAdmins" (user_id);
