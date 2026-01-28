-- Table to store aggregated stats per player per pelada
CREATE TABLE IF NOT EXISTS "PeladaPlayerStats" (
  "pelada_id" INTEGER NOT NULL,
  "player_id" INTEGER NOT NULL,
  "goals" INTEGER DEFAULT 0,
  "assists" INTEGER DEFAULT 0,
  "own_goals" INTEGER DEFAULT 0,
  PRIMARY KEY ("pelada_id", "player_id"),
  FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id") ON DELETE CASCADE,
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id") ON DELETE CASCADE
);
--;;
CREATE INDEX IF NOT EXISTS "PeladaPlayerStats_index_pelada" ON "PeladaPlayerStats" ("pelada_id");
--;;
-- Trigger for INSERT on MatchEvents
CREATE TRIGGER IF NOT EXISTS "MatchEvents_Insert_Stats"
AFTER INSERT ON "MatchEvents"
BEGIN
  INSERT INTO "PeladaPlayerStats" ("pelada_id", "player_id", "goals", "assists", "own_goals")
  SELECT m.pelada_id, NEW.player_id,
         CASE WHEN NEW.event_type = 'goal' THEN 1 ELSE 0 END,
         CASE WHEN NEW.event_type = 'assist' THEN 1 ELSE 0 END,
         CASE WHEN NEW.event_type = 'own_goal' THEN 1 ELSE 0 END
  FROM "Matches" m WHERE m.id = NEW.match_id
  ON CONFLICT("pelada_id", "player_id") DO UPDATE SET
    "goals" = "goals" + CASE WHEN NEW.event_type = 'goal' THEN 1 ELSE 0 END,
    "assists" = "assists" + CASE WHEN NEW.event_type = 'assist' THEN 1 ELSE 0 END,
    "own_goals" = "own_goals" + CASE WHEN NEW.event_type = 'own_goal' THEN 1 ELSE 0 END;
END;
--;;
-- Trigger for DELETE on MatchEvents
CREATE TRIGGER IF NOT EXISTS "MatchEvents_Delete_Stats"
AFTER DELETE ON "MatchEvents"
BEGIN
  UPDATE "PeladaPlayerStats"
  SET
    "goals" = "goals" - CASE WHEN OLD.event_type = 'goal' THEN 1 ELSE 0 END,
    "assists" = "assists" - CASE WHEN OLD.event_type = 'assist' THEN 1 ELSE 0 END,
    "own_goals" = "own_goals" - CASE WHEN OLD.event_type = 'own_goal' THEN 1 ELSE 0 END
  WHERE "player_id" = OLD.player_id
    AND "pelada_id" = (SELECT pelada_id FROM "Matches" WHERE id = OLD.match_id);
END;
--;;
-- Initial Population (Backfill)
INSERT INTO "PeladaPlayerStats" ("pelada_id", "player_id", "goals", "assists", "own_goals")
SELECT m.pelada_id, e.player_id,
       SUM(CASE WHEN e.event_type='goal' THEN 1 ELSE 0 END),
       SUM(CASE WHEN e.event_type='assist' THEN 1 ELSE 0 END),
       SUM(CASE WHEN e.event_type='own_goal' THEN 1 ELSE 0 END)
FROM "MatchEvents" e
JOIN "Matches" m ON m.id = e.match_id
GROUP BY m.pelada_id, e.player_id
ON CONFLICT("pelada_id", "player_id") DO UPDATE SET
  "goals" = excluded.goals,
  "assists" = excluded.assists,
  "own_goals" = excluded.own_goals;