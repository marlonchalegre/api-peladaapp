ALTER TABLE "OrganizationPlayers" ADD COLUMN "member_type" VARCHAR NOT NULL DEFAULT 'diarista';

--;;

CREATE TABLE IF NOT EXISTS "peladaattendance_new" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "pelada_id" INTEGER NOT NULL,
  "player_id" INTEGER NOT NULL,
  "status" VARCHAR NOT NULL CHECK ("status" IN ('confirmed', 'declined', 'pending', 'waitlist')),
  "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE ("pelada_id", "player_id"),
  FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id"),
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id")
);

--;;

INSERT INTO "peladaattendance_new" ("id", "pelada_id", "player_id", "status", "updated_at")
SELECT "id", "pelada_id", "player_id", "status", "updated_at" FROM "peladaattendance";

--;;

DROP TABLE "peladaattendance";

--;;

ALTER TABLE "peladaattendance_new" RENAME TO "peladaattendance";

--;;

CREATE INDEX IF NOT EXISTS "Attendance_index_pelada" ON "peladaattendance" ("pelada_id");
