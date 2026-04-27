-- OrganizationInvitations
CREATE TABLE IF NOT EXISTS "OrganizationInvitations" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "organization_id" INTEGER NOT NULL,
  "email" VARCHAR, -- If present, it's a personal invitation
  "token" VARCHAR UNIQUE NOT NULL, -- Unique token for the link
  "status" VARCHAR DEFAULT 'pending', -- pending, accepted, rejected
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "invited_by" INTEGER, -- User who invited
  FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id"),
  FOREIGN KEY ("invited_by") REFERENCES "Users"("id")
);
--;;

CREATE INDEX IF NOT EXISTS "OrgInvitations_index_org" ON "OrganizationInvitations" ("organization_id");
--;;
CREATE INDEX IF NOT EXISTS "OrgInvitations_index_email" ON "OrganizationInvitations" ("email");
--;;
CREATE INDEX IF NOT EXISTS "OrgInvitations_index_token" ON "OrganizationInvitations" ("token");
