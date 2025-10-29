-- Match Events to record assists, goals and own goals per player per match
CREATE TABLE IF NOT EXISTS "MatchEvents" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "match_id" INTEGER NOT NULL,
  "player_id" INTEGER NOT NULL, -- OrganizationPlayers.id
  "event_type" VARCHAR NOT NULL CHECK (event_type IN ('assist','goal','own_goal')),
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY ("match_id") REFERENCES "Matches"("id"),
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id")
);
CREATE INDEX IF NOT EXISTS "MatchEvents_index_match" ON "MatchEvents" ("match_id");
CREATE INDEX IF NOT EXISTS "MatchEvents_index_player" ON "MatchEvents" ("player_id");
