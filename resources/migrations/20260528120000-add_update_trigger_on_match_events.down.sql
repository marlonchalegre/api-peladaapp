DROP TRIGGER IF EXISTS matchevents_recalculate_score_trigger ON "MatchEvents";
--;;
DROP FUNCTION IF EXISTS trigger_recalculate_match_score();
--;;
DROP FUNCTION IF EXISTS recalculate_match_score(UUID);
--;;
DROP TRIGGER IF EXISTS matchevents_update_stats ON "MatchEvents";
--;;
DROP FUNCTION IF EXISTS update_pelada_player_stats_on_update();
