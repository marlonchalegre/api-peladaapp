CREATE TABLE IF NOT EXISTS "Votes" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "pelada_id" INTEGER NOT NULL, "voter_id" INTEGER NOT NULL, "target_id" INTEGER NOT NULL, "stars" INTEGER NOT NULL CHECK ("stars" >= 1 AND "stars" <= 5), "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE ("pelada_id", "voter_id", "target_id"), FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id"), FOREIGN KEY ("voter_id") REFERENCES "OrganizationPlayers"("id"), FOREIGN KEY ("target_id") REFERENCES "OrganizationPlayers"("id"));
--;;
CREATE INDEX IF NOT EXISTS "Votes_index_pelada" ON "Votes" ("pelada_id");
--;;
CREATE INDEX IF NOT EXISTS "Votes_index_target" ON "Votes" ("pelada_id", "target_id");
