-- Add ON DELETE CASCADE to core tables to allow deleting organizations and their related data

-- 1. OrganizationPlayers
ALTER TABLE "OrganizationPlayers" DROP CONSTRAINT IF EXISTS "OrganizationPlayers_organization_id_fkey";
--;;
ALTER TABLE "OrganizationPlayers" ADD CONSTRAINT "OrganizationPlayers_organization_id_fkey" 
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE;
--;;

-- 2. Peladas
ALTER TABLE "Peladas" DROP CONSTRAINT IF EXISTS "Peladas_organization_id_fkey";
--;;
ALTER TABLE "Peladas" ADD CONSTRAINT "Peladas_organization_id_fkey" 
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE;
--;;

-- 3. OrganizationInvitations
ALTER TABLE "OrganizationInvitations" DROP CONSTRAINT IF EXISTS "OrganizationInvitations_organization_id_fkey";
--;;
ALTER TABLE "OrganizationInvitations" ADD CONSTRAINT "OrganizationInvitations_organization_id_fkey" 
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE;
--;;

-- 4. Attendance
ALTER TABLE "Attendance" DROP CONSTRAINT IF EXISTS "Attendance_pelada_id_fkey";
--;;
ALTER TABLE "Attendance" ADD CONSTRAINT "Attendance_pelada_id_fkey" 
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE CASCADE;
--;;

ALTER TABLE "Attendance" DROP CONSTRAINT IF EXISTS "Attendance_player_id_fkey";
--;;
ALTER TABLE "Attendance" ADD CONSTRAINT "Attendance_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

-- 5. Teams
ALTER TABLE "Teams" DROP CONSTRAINT IF EXISTS "Teams_pelada_id_fkey";
--;;
ALTER TABLE "Teams" ADD CONSTRAINT "Teams_pelada_id_fkey" 
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE CASCADE;
--;;

-- 6. TeamPlayers
ALTER TABLE "TeamPlayers" DROP CONSTRAINT IF EXISTS "TeamPlayers_team_id_fkey";
--;;
ALTER TABLE "TeamPlayers" ADD CONSTRAINT "TeamPlayers_team_id_fkey" 
  FOREIGN KEY (team_id) REFERENCES "Teams"(id) ON DELETE CASCADE;
--;;

ALTER TABLE "TeamPlayers" DROP CONSTRAINT IF EXISTS "TeamPlayers_player_id_fkey";
--;;
ALTER TABLE "TeamPlayers" ADD CONSTRAINT "TeamPlayers_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

-- 7. Matches
ALTER TABLE "Matches" DROP CONSTRAINT IF EXISTS "Matches_pelada_id_fkey";
--;;
ALTER TABLE "Matches" ADD CONSTRAINT "Matches_pelada_id_fkey" 
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE CASCADE;
--;;

-- 8. MatchEvents
ALTER TABLE "MatchEvents" DROP CONSTRAINT IF EXISTS "MatchEvents_match_id_fkey";
--;;
ALTER TABLE "MatchEvents" ADD CONSTRAINT "MatchEvents_match_id_fkey" 
  FOREIGN KEY (match_id) REFERENCES "Matches"(id) ON DELETE CASCADE;
--;;

-- 9. Votes
ALTER TABLE "Votes" DROP CONSTRAINT IF EXISTS "Votes_pelada_id_fkey";
--;;
ALTER TABLE "Votes" ADD CONSTRAINT "Votes_pelada_id_fkey" 
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE CASCADE;
