CREATE TABLE IF NOT EXISTS "PeladaPlayerStats" (
  pelada_id UUID NOT NULL,
  player_id UUID NOT NULL,
  goals INTEGER DEFAULT 0,
  assists INTEGER DEFAULT 0,
  own_goals INTEGER DEFAULT 0,
  PRIMARY KEY (pelada_id, player_id),
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE
);
--;;
CREATE INDEX IF NOT EXISTS peladaplayerstats_index_pelada ON "PeladaPlayerStats" (pelada_id);
--;;
CREATE OR REPLACE FUNCTION update_pelada_player_stats_on_insert()
RETURNS TRIGGER AS $$
DECLARE
  v_pelada_id UUID;
BEGIN
  SELECT pelada_id INTO v_pelada_id FROM "Matches" WHERE id = NEW.match_id;

  INSERT INTO "PeladaPlayerStats" (pelada_id, player_id, goals, assists, own_goals)
  VALUES (
    v_pelada_id, NEW.player_id,
    CASE WHEN NEW.event_type = 'goal' THEN 1 ELSE 0 END,
    CASE WHEN NEW.event_type = 'assist' THEN 1 ELSE 0 END,
    CASE WHEN NEW.event_type = 'own_goal' THEN 1 ELSE 0 END
  )
  ON CONFLICT(pelada_id, player_id) DO UPDATE SET
    goals = "PeladaPlayerStats".goals + CASE WHEN NEW.event_type = 'goal' THEN 1 ELSE 0 END,
    assists = "PeladaPlayerStats".assists + CASE WHEN NEW.event_type = 'assist' THEN 1 ELSE 0 END,
    own_goals = "PeladaPlayerStats".own_goals + CASE WHEN NEW.event_type = 'own_goal' THEN 1 ELSE 0 END;
    
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
--;;
CREATE OR REPLACE TRIGGER matchevents_insert_stats
AFTER INSERT ON "MatchEvents"
FOR EACH ROW EXECUTE FUNCTION update_pelada_player_stats_on_insert();
--;;
CREATE OR REPLACE FUNCTION update_pelada_player_stats_on_delete()
RETURNS TRIGGER AS $$
DECLARE
  v_pelada_id UUID;
BEGIN
  SELECT pelada_id INTO v_pelada_id FROM "Matches" WHERE id = OLD.match_id;

  UPDATE "PeladaPlayerStats"
  SET
    goals = goals - CASE WHEN OLD.event_type = 'goal' THEN 1 ELSE 0 END,
    assists = assists - CASE WHEN OLD.event_type = 'assist' THEN 1 ELSE 0 END,
    own_goals = own_goals - CASE WHEN OLD.event_type = 'own_goal' THEN 1 ELSE 0 END
  WHERE player_id = OLD.player_id
    AND pelada_id = v_pelada_id;

  RETURN OLD;
END;
$$ LANGUAGE plpgsql;
--;;
CREATE OR REPLACE TRIGGER matchevents_delete_stats
AFTER DELETE ON "MatchEvents"
FOR EACH ROW EXECUTE FUNCTION update_pelada_player_stats_on_delete();
--;;
INSERT INTO "PeladaPlayerStats" (pelada_id, player_id, goals, assists, own_goals)
SELECT m.pelada_id, e.player_id,
       SUM(CASE WHEN e.event_type='goal' THEN 1 ELSE 0 END),
       SUM(CASE WHEN e.event_type='assist' THEN 1 ELSE 0 END),
       SUM(CASE WHEN e.event_type='own_goal' THEN 1 ELSE 0 END)
FROM "MatchEvents" e
JOIN "Matches" m ON m.id = e.match_id
GROUP BY m.pelada_id, e.player_id
ON CONFLICT(pelada_id, player_id) DO UPDATE SET
  goals = excluded.goals,
  assists = excluded.assists,
  own_goals = excluded.own_goals;