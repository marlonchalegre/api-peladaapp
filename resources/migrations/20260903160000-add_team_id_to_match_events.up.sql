ALTER TABLE "MatchEvents" ADD COLUMN IF NOT EXISTS team_id UUID REFERENCES "Teams"(id) ON DELETE SET NULL;
--;;
CREATE INDEX IF NOT EXISTS matchevents_index_team ON "MatchEvents"(team_id);
--;;
UPDATE "MatchEvents" me
SET team_id = COALESCE(
  (SELECT ml.team_id FROM "MatchLineups" ml WHERE ml.match_id = me.match_id AND ml.player_id = me.player_id LIMIT 1),
  (SELECT tp.team_id FROM "TeamPlayers" tp JOIN "Teams" t ON t.id = tp.team_id WHERE t.pelada_id = (SELECT pelada_id FROM "Matches" WHERE id = me.match_id) AND tp.player_id = me.player_id LIMIT 1)
)
WHERE team_id IS NULL;
--;;
CREATE OR REPLACE FUNCTION set_match_event_team_id()
RETURNS TRIGGER AS $$
BEGIN
  IF (TG_OP = 'INSERT' AND NEW.team_id IS NULL) OR (TG_OP = 'UPDATE' AND OLD.player_id <> NEW.player_id) THEN
    SELECT team_id INTO NEW.team_id
    FROM "MatchLineups"
    WHERE match_id = NEW.match_id AND player_id = NEW.player_id
    LIMIT 1;

    IF NEW.team_id IS NULL THEN
      SELECT tp.team_id INTO NEW.team_id
      FROM "TeamPlayers" tp
      JOIN "Teams" t ON t.id = tp.team_id
      WHERE t.pelada_id = (SELECT pelada_id FROM "Matches" WHERE id = NEW.match_id)
        AND tp.player_id = NEW.player_id
      LIMIT 1;
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
--;;
DROP TRIGGER IF EXISTS matchevents_set_team_id ON "MatchEvents";
--;;
CREATE OR REPLACE TRIGGER matchevents_set_team_id
BEFORE INSERT OR UPDATE OF player_id ON "MatchEvents"
FOR EACH ROW EXECUTE FUNCTION set_match_event_team_id();
--;;
CREATE OR REPLACE FUNCTION recalculate_match_score(p_match_id UUID)
RETURNS VOID AS $$
DECLARE
  v_home_team_id UUID;
  v_away_team_id UUID;
  v_pelada_id UUID;
  v_home_score INT := 0;
  v_away_score INT := 0;
  r RECORD;
  v_player_team_id UUID;
BEGIN
  SELECT home_team_id, away_team_id, pelada_id 
  INTO v_home_team_id, v_away_team_id, v_pelada_id
  FROM "Matches" WHERE id = p_match_id;

  IF v_home_team_id IS NULL OR v_away_team_id IS NULL THEN
    RETURN;
  END IF;

  FOR r IN 
    SELECT player_id, event_type, team_id 
    FROM "MatchEvents" 
    WHERE match_id = p_match_id AND event_type IN ('goal', 'own_goal')
  LOOP
    v_player_team_id := r.team_id;

    IF v_player_team_id IS NULL THEN
      SELECT team_id INTO v_player_team_id 
      FROM "MatchLineups" 
      WHERE match_id = p_match_id AND player_id = r.player_id;
    END IF;

    IF v_player_team_id IS NULL THEN
      SELECT tp.team_id INTO v_player_team_id
      FROM "TeamPlayers" tp
      JOIN "Teams" t ON t.id = tp.team_id
      WHERE t.pelada_id = v_pelada_id
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
