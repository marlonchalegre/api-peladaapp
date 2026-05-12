CREATE TABLE IF NOT EXISTS "OrganizationInvitations" (
  id SERIAL PRIMARY KEY,
  organization_id INTEGER NOT NULL,
  email VARCHAR, 
  token VARCHAR UNIQUE NOT NULL, 
  status VARCHAR DEFAULT 'pending', 
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  invited_by INTEGER, 
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id),
  FOREIGN KEY (invited_by) REFERENCES "Users"(id)
);
--;;
CREATE INDEX IF NOT EXISTS orginvitations_index_org ON "OrganizationInvitations" (organization_id);
--;;
CREATE INDEX IF NOT EXISTS orginvitations_index_email ON "OrganizationInvitations" (email);
--;;
CREATE INDEX IF NOT EXISTS orginvitations_index_token ON "OrganizationInvitations" (token);
