ALTER TYPE match_event_type ADD VALUE IF NOT EXISTS 'drible';
--;;
ALTER TYPE match_event_type ADD VALUE IF NOT EXISTS 'chute';
--;;
ALTER TYPE match_event_type ADD VALUE IF NOT EXISTS 'falta';
--;;
ALTER TYPE match_event_type ADD VALUE IF NOT EXISTS 'furada';
--;;
ALTER TYPE match_event_type ADD VALUE IF NOT EXISTS 'defesa';
--;;
ALTER TYPE match_event_type ADD VALUE IF NOT EXISTS 'vish';
--;;
ALTER TABLE "MatchEvents" DROP CONSTRAINT IF EXISTS "MatchEvents_event_type_check";
--;;
ALTER TABLE "MatchEvents" DROP CONSTRAINT IF EXISTS "matchevents_event_type_check";

