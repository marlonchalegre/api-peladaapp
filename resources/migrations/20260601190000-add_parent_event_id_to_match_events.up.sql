ALTER TABLE "MatchEvents" ADD COLUMN parent_event_id UUID REFERENCES "MatchEvents"(id) ON DELETE CASCADE;
