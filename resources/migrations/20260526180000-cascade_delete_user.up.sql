-- Reconfigure foreign keys referencing Users(id) and OrganizationPlayers(id) to cascade on delete

-- 1. OrganizationPlayers
ALTER TABLE "OrganizationPlayers" DROP CONSTRAINT IF EXISTS "OrganizationPlayers_user_id_fkey";
--;;
ALTER TABLE "OrganizationPlayers" DROP CONSTRAINT IF EXISTS "organizationplayers_user_id_fkey";
--;;
ALTER TABLE "OrganizationPlayers" ADD CONSTRAINT "OrganizationPlayers_user_id_fkey" 
  FOREIGN KEY (user_id) REFERENCES "Users"(id) ON DELETE CASCADE;
--;;

-- 2. password_reset_tokens
ALTER TABLE "password_reset_tokens" DROP CONSTRAINT IF EXISTS "password_reset_tokens_user_id_fkey";
--;;
ALTER TABLE "password_reset_tokens" ADD CONSTRAINT "password_reset_tokens_user_id_fkey" 
  FOREIGN KEY (user_id) REFERENCES "Users"(id) ON DELETE CASCADE;
--;;

-- 3. OrganizationInvitations
ALTER TABLE "OrganizationInvitations" DROP CONSTRAINT IF EXISTS "OrganizationInvitations_invited_by_fkey";
--;;
ALTER TABLE "OrganizationInvitations" DROP CONSTRAINT IF EXISTS "organizationinvitations_invited_by_fkey";
--;;
ALTER TABLE "OrganizationInvitations" ADD CONSTRAINT "OrganizationInvitations_invited_by_fkey" 
  FOREIGN KEY (invited_by) REFERENCES "Users"(id) ON DELETE SET NULL;
--;;

-- 4. Organizations
ALTER TABLE "Organizations" DROP CONSTRAINT IF EXISTS "Organizations_owner_id_fkey";
--;;
ALTER TABLE "Organizations" DROP CONSTRAINT IF EXISTS "organizations_owner_id_fkey";
--;;
ALTER TABLE "Organizations" ADD CONSTRAINT "Organizations_owner_id_fkey" 
  FOREIGN KEY (owner_id) REFERENCES "Users"(id) ON DELETE SET NULL;
--;;

-- 5. Votes (voter_id and target_id)
ALTER TABLE "Votes" DROP CONSTRAINT IF EXISTS "Votes_voter_id_fkey";
--;;
ALTER TABLE "Votes" DROP CONSTRAINT IF EXISTS "votes_voter_id_fkey";
--;;
ALTER TABLE "Votes" ADD CONSTRAINT "Votes_voter_id_fkey" 
  FOREIGN KEY (voter_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

ALTER TABLE "Votes" DROP CONSTRAINT IF EXISTS "Votes_target_id_fkey";
--;;
ALTER TABLE "Votes" DROP CONSTRAINT IF EXISTS "votes_target_id_fkey";
--;;
ALTER TABLE "Votes" ADD CONSTRAINT "Votes_target_id_fkey" 
  FOREIGN KEY (target_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

-- 6. Peladas (home_fixed_goalkeeper_id and away_fixed_goalkeeper_id)
ALTER TABLE "Peladas" DROP CONSTRAINT IF EXISTS "Peladas_home_fixed_goalkeeper_id_fkey";
--;;
ALTER TABLE "Peladas" DROP CONSTRAINT IF EXISTS "peladas_home_fixed_goalkeeper_id_fkey";
--;;
ALTER TABLE "Peladas" ADD CONSTRAINT "Peladas_home_fixed_goalkeeper_id_fkey" 
  FOREIGN KEY (home_fixed_goalkeeper_id) REFERENCES "OrganizationPlayers"(id) ON DELETE SET NULL;
--;;

ALTER TABLE "Peladas" DROP CONSTRAINT IF EXISTS "Peladas_away_fixed_goalkeeper_id_fkey";
--;;
ALTER TABLE "Peladas" DROP CONSTRAINT IF EXISTS "peladas_away_fixed_goalkeeper_id_fkey";
--;;
ALTER TABLE "Peladas" ADD CONSTRAINT "Peladas_away_fixed_goalkeeper_id_fkey" 
  FOREIGN KEY (away_fixed_goalkeeper_id) REFERENCES "OrganizationPlayers"(id) ON DELETE SET NULL;
--;;

-- 7. TeamPlayers
ALTER TABLE "TeamPlayers" DROP CONSTRAINT IF EXISTS "TeamPlayers_player_id_fkey";
--;;
ALTER TABLE "TeamPlayers" DROP CONSTRAINT IF EXISTS "teamplayers_player_id_fkey";
--;;
ALTER TABLE "TeamPlayers" ADD CONSTRAINT "TeamPlayers_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

-- 8. MatchSubstitutions
ALTER TABLE "MatchSubstitutions" DROP CONSTRAINT IF EXISTS "MatchSubstitutions_out_player_id_fkey";
--;;
ALTER TABLE "MatchSubstitutions" DROP CONSTRAINT IF EXISTS "matchsubstitutions_out_player_id_fkey";
--;;
ALTER TABLE "MatchSubstitutions" ADD CONSTRAINT "MatchSubstitutions_out_player_id_fkey" 
  FOREIGN KEY (out_player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

ALTER TABLE "MatchSubstitutions" DROP CONSTRAINT IF EXISTS "MatchSubstitutions_in_player_id_fkey";
--;;
ALTER TABLE "MatchSubstitutions" DROP CONSTRAINT IF EXISTS "matchsubstitutions_in_player_id_fkey";
--;;
ALTER TABLE "MatchSubstitutions" ADD CONSTRAINT "MatchSubstitutions_in_player_id_fkey" 
  FOREIGN KEY (in_player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

-- 9. MatchEvents
ALTER TABLE "MatchEvents" DROP CONSTRAINT IF EXISTS "MatchEvents_player_id_fkey";
--;;
ALTER TABLE "MatchEvents" DROP CONSTRAINT IF EXISTS "matchevents_player_id_fkey";
--;;
ALTER TABLE "MatchEvents" ADD CONSTRAINT "MatchEvents_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

-- 10. Statistics
ALTER TABLE "Statistics" DROP CONSTRAINT IF EXISTS "Statistics_player_id_fkey";
--;;
ALTER TABLE "Statistics" DROP CONSTRAINT IF EXISTS "statistics_player_id_fkey";
--;;
ALTER TABLE "Statistics" ADD CONSTRAINT "Statistics_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

-- 11. Attendance Legacy Constraints cleanup
ALTER TABLE "Attendance" DROP CONSTRAINT IF EXISTS "peladaattendance_player_id_fkey";
--;;
ALTER TABLE "Attendance" DROP CONSTRAINT IF EXISTS "Attendance_player_id_fkey";
--;;
ALTER TABLE "Attendance" ADD CONSTRAINT "Attendance_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE;
--;;

ALTER TABLE "Attendance" DROP CONSTRAINT IF EXISTS "peladaattendance_pelada_id_fkey";
--;;
ALTER TABLE "Attendance" DROP CONSTRAINT IF EXISTS "Attendance_pelada_id_fkey";
--;;
ALTER TABLE "Attendance" ADD CONSTRAINT "Attendance_pelada_id_fkey" 
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE CASCADE;
