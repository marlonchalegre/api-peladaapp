ALTER TABLE "MatchEvents"
  ALTER COLUMN session_time_ms TYPE BIGINT,
  ALTER COLUMN match_time_ms TYPE BIGINT;
--;;
ALTER TABLE "Matches" ALTER COLUMN timer_accumulated_ms TYPE BIGINT;
--;;
ALTER TABLE "Peladas" ALTER COLUMN timer_accumulated_ms TYPE BIGINT;
