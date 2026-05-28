CREATE OR REPLACE FUNCTION update_pelada_player_stats_on_update()
RETURNS TRIGGER AS $$
DECLARE
  v_old_pelada_id UUID;
  v_new_pelada_id UUID;
BEGIN
  -- If player_id or event_type or match_id has changed
  IF (OLD.player_id <> NEW.player_id OR OLD.event_type <> NEW.event_type OR OLD.match_id <> NEW.match_id) THEN
    -- 1. Decrement old stats
    SELECT pelada_id INTO v_old_pelada_id FROM "Matches" WHERE id = OLD.match_id;
    IF v_old_pelada_id IS NOT NULL THEN
      UPDATE "PeladaPlayerStats"
      SET
        goals = goals - CASE WHEN OLD.event_type = 'goal' THEN 1 ELSE 0 END,
        assists = assists - CASE WHEN OLD.event_type = 'assist' THEN 1 ELSE 0 END,
        own_goals = own_goals - CASE WHEN OLD.event_type = 'own_goal' THEN 1 ELSE 0 END
      WHERE player_id = OLD.player_id
        AND pelada_id = v_old_pelada_id;
    END IF;

    -- 2. Increment new stats
    SELECT pelada_id INTO v_new_pelada_id FROM "Matches" WHERE id = NEW.match_id;
    IF v_new_pelada_id IS NOT NULL THEN
      INSERT INTO "PeladaPlayerStats" (pelada_id, player_id, goals, assists, own_goals)
      VALUES (
        v_new_pelada_id, NEW.player_id,
        CASE WHEN NEW.event_type = 'goal' THEN 1 ELSE 0 END,
        CASE WHEN NEW.event_type = 'assist' THEN 1 ELSE 0 END,
        CASE WHEN NEW.event_type = 'own_goal' THEN 1 ELSE 0 END
      )
      ON CONFLICT(pelada_id, player_id) DO UPDATE SET
        goals = "PeladaPlayerStats".goals + CASE WHEN NEW.event_type = 'goal' THEN 1 ELSE 0 END,
        assists = "PeladaPlayerStats".assists + CASE WHEN NEW.event_type = 'assist' THEN 1 ELSE 0 END,
        own_goals = "PeladaPlayerStats".own_goals + CASE WHEN NEW.event_type = 'own_goal' THEN 1 ELSE 0 END;
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
--;;
CREATE OR REPLACE TRIGGER matchevents_update_stats
AFTER UPDATE ON "MatchEvents"
FOR EACH ROW EXECUTE FUNCTION update_pelada_player_stats_on_update();
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
  -- Get match teams
  SELECT home_team_id, away_team_id INTO v_home_team_id, v_away_team_id
  FROM "Matches" WHERE id = p_match_id;

  IF v_home_team_id IS NULL OR v_away_team_id IS NULL THEN
    RETURN;
  END IF;

  -- Loop through all goals and own_goals for this match
  FOR r IN 
    SELECT player_id, event_type 
    FROM "MatchEvents" 
    WHERE match_id = p_match_id AND event_type IN ('goal', 'own_goal')
  LOOP
    -- 1. Find player's team for this match (check MatchLineups first)
    SELECT team_id INTO v_player_team_id 
    FROM "MatchLineups" 
    WHERE match_id = p_match_id AND player_id = r.player_id;

    -- 2. Fallback to TeamPlayers if not found in MatchLineups
    IF v_player_team_id IS NULL THEN
      SELECT tp.team_id INTO v_player_team_id
      FROM "TeamPlayers" tp
      JOIN "Teams" t ON t.id = tp.team_id
      WHERE t.pelada_id = (SELECT pelada_id FROM "Matches" WHERE id = p_match_id)
        AND tp.player_id = r.player_id
      LIMIT 1;
    END IF;

    -- 3. Adjust score
    IF v_player_team_id = v_home_team_id THEN
      IF r.event_type = 'goal' THEN
        v_home_score := v_home_score + 1;
      ELSE
        v_away_score := v_away_score + 1; -- own_goal by home team scores for away team
      END IF;
    ELSIF v_player_team_id = v_away_team_id THEN
      IF r.event_type = 'goal' THEN
        v_away_score := v_away_score + 1;
      ELSE
        v_home_score := v_home_score + 1; -- own_goal by away team scores for home team
      END IF;
    END IF;
  END LOOP;

  -- Update the Match score
  UPDATE "Matches"
  SET home_score = v_home_score,
      away_score = v_away_score
  WHERE id = p_match_id;
END;
$$ LANGUAGE plpgsql;
--;;
CREATE OR REPLACE FUNCTION trigger_recalculate_match_score()
RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
    PERFORM recalculate_match_score(NEW.match_id);
  END IF;
  IF TG_OP = 'DELETE' OR TG_OP = 'UPDATE' THEN
    PERFORM recalculate_match_score(OLD.match_id);
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;
--;;
CREATE OR REPLACE TRIGGER matchevents_recalculate_score_trigger
AFTER INSERT OR UPDATE OR DELETE ON "MatchEvents"
FOR EACH ROW EXECUTE FUNCTION trigger_recalculate_match_score();
