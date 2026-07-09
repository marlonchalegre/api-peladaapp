ALTER TABLE "MatchSubstitutions" DROP CONSTRAINT IF EXISTS "MatchSubstitutions_match_id_fkey";
--;;
ALTER TABLE "MatchSubstitutions" ADD CONSTRAINT "MatchSubstitutions_match_id_fkey" 
  FOREIGN KEY (match_id) REFERENCES "Matches"(id) ON DELETE CASCADE;
