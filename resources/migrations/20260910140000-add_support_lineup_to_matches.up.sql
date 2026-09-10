ALTER TABLE "Matches" ADD COLUMN IF NOT EXISTS support_camera_player_id UUID REFERENCES "OrganizationPlayers"(id) ON DELETE SET NULL;
--;;
ALTER TABLE "Matches" ADD COLUMN IF NOT EXISTS support_stats_player_id UUID REFERENCES "OrganizationPlayers"(id) ON DELETE SET NULL;
--;;
CREATE INDEX IF NOT EXISTS matches_index_support_camera_player ON "Matches" (support_camera_player_id);
--;;
CREATE INDEX IF NOT EXISTS matches_index_support_stats_player ON "Matches" (support_stats_player_id);
