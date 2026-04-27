CREATE TABLE IF NOT EXISTS "OrganizationPlayers" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "organization_id" INTEGER NOT NULL, "user_id" INTEGER NOT NULL, "grade" REAL, "position_id" INTEGER, FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id"), FOREIGN KEY ("user_id") REFERENCES "Users"("id"), FOREIGN KEY ("position_id") REFERENCES "Positions"("id"));
--;;
CREATE INDEX IF NOT EXISTS "OrgPlayers_index_org" ON "OrganizationPlayers" ("organization_id");
--;;
CREATE INDEX IF NOT EXISTS "OrgPlayers_index_user" ON "OrganizationPlayers" ("user_id");
