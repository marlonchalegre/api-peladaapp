DROP INDEX IF EXISTS matches_index_support_camera_player;
--;;
DROP INDEX IF EXISTS matches_index_support_stats_player;
--;;
ALTER TABLE "Matches" DROP COLUMN IF EXISTS support_camera_player_id;
--;;
ALTER TABLE "Matches" DROP COLUMN IF EXISTS support_stats_player_id;
