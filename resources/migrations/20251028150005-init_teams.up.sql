CREATE TABLE IF NOT EXISTS "Teams" ("id" INTEGER PRIMARY KEY AUTOINCREMENT, "pelada_id" INTEGER NOT NULL, "name" VARCHAR, FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id"));
CREATE INDEX IF NOT EXISTS "Teams_index_pelada" ON "Teams" ("pelada_id");
