-- Additional performance indexes for foreign keys that are often queried
CREATE INDEX IF NOT EXISTS "Attendance_index_player" ON "peladaattendance" ("player_id");
--;;
CREATE INDEX IF NOT EXISTS "Matches_index_home_team" ON "Matches" ("home_team_id");
--;;
CREATE INDEX IF NOT EXISTS "Matches_index_away_team" ON "Matches" ("away_team_id");
