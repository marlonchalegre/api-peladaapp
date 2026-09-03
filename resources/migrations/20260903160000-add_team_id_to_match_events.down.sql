DROP TRIGGER IF EXISTS matchevents_set_team_id ON "MatchEvents";
--;;
DROP FUNCTION IF EXISTS set_match_event_team_id();
--;;
DROP INDEX IF EXISTS matchevents_index_team;
--;;
ALTER TABLE "MatchEvents" DROP COLUMN IF EXISTS team_id;
--;;
CREATE OR REPLACE FUNCTION recalculate_match_score(p_match_id UUID)
RETURNS VOID AS $$
DECLARE
  v_home_team_id UUID;
  v_away_team_id UUID;
  v_home_score INT := 0;
  v_away_score INT := 0;
  r RECORD;
  v_player_team_id UUID;
BEGIN
  SELECT home_team_id, away_team_id INTO v_home_team_id, v_away_team_id
  FROM "Matches" WHERE id = p_match_id;

  IF v_home_team_id IS NULL OR v_away_team_id IS NULL THEN
    RETURN;
  END IF;

  FOR r IN 
    SELECT player_id, event_type 
    FROM "MatchEvents" 
    WHERE match_id = p_match_id AND event_type IN ('goal', 'own_goal')
  LOOP
    SELECT team_id INTO v_player_team_id 
    FROM "MatchLineups" 
    WHERE match_id = p_match_id AND player_id = r.player_id;

    IF v_player_team_id IS NULL THEN
      SELECT tp.team_id INTO v_player_team_id
      FROM "TeamPlayers" tp
      JOIN "Teams" t ON t.id = tp.team_id
      WHERE t.pelada_id = (SELECT pelada_id FROM "Matches" WHERE id = p_match_id)
        AND tp.player_id = r.player_id
      LIMIT 1;
    END IF;

    IF v_player_team_id = v_home_team_id THEN
      IF r.event_type = 'goal' THEN
        v_home_score := v_home_score + 1;
      ELSE
        v_away_score := v_away_score + 1;
      END IF;
    ELSIF v_player_team_id = v_away_team_id THEN
      IF r.event_type = 'goal' THEN
        v_away_score := v_away_score + 1;
      ELSE
        v_home_score := v_home_score + 1;
      END IF;
    END IF;
  END LOOP;

  UPDATE "Matches"
  SET home_score = v_home_score,
      away_score = v_away_score
  WHERE id = p_match_id;
END;
$$ LANGUAGE plpgsql;
