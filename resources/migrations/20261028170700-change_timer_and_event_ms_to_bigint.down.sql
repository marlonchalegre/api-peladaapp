ALTER TABLE "MatchEvents"
  ALTER COLUMN session_time_ms TYPE INTEGER,
  ALTER COLUMN match_time_ms TYPE INTEGER;
--;;
ALTER TABLE "Matches" ALTER COLUMN timer_accumulated_ms TYPE INTEGER;
--;;
ALTER TABLE "Peladas" ALTER COLUMN timer_accumulated_ms TYPE INTEGER;
