-- Pelada Attendance
CREATE TABLE IF NOT EXISTS "peladaattendance" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "pelada_id" INTEGER NOT NULL,
  "player_id" INTEGER NOT NULL,
  "status" VARCHAR NOT NULL CHECK ("status" IN ('confirmed', 'declined', 'pending')),
  "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE ("pelada_id", "player_id"),
  FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id"),
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id")
);
--;;

CREATE INDEX IF NOT EXISTS "Attendance_index_pelada" ON "peladaattendance" ("pelada_id");
