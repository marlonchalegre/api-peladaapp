-- Optimization for finding votes by voter in a pelada
CREATE INDEX IF NOT EXISTS "Votes_index_pelada_voter" ON "Votes" ("pelada_id", "voter_id");
