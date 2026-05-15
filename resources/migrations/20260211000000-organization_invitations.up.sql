CREATE TABLE IF NOT EXISTS "OrganizationInvitations" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL,
  email VARCHAR, 
  token VARCHAR UNIQUE NOT NULL, 
  status VARCHAR DEFAULT 'pending', 
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  invited_by UUID, 
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id),
  FOREIGN KEY (invited_by) REFERENCES "Users"(id)
);
--;;
CREATE INDEX IF NOT EXISTS orginvitations_index_org ON "OrganizationInvitations" (organization_id);
--;;
CREATE INDEX IF NOT EXISTS orginvitations_index_email ON "OrganizationInvitations" (email);
--;;
CREATE INDEX IF NOT EXISTS orginvitations_index_token ON "OrganizationInvitations" (token);
