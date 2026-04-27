-- Users
CREATE TABLE IF NOT EXISTS "Users" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "email" VARCHAR UNIQUE, "password" VARCHAR, "name" VARCHAR);
--;;
CREATE INDEX IF NOT EXISTS "Users_index_email" ON "Users" ("email");
