-- Revert constraints referencing Users(id) and OrganizationPlayers(id) to original restrict behavior

ALTER TABLE "OrganizationPlayers" DROP CONSTRAINT IF EXISTS "OrganizationPlayers_user_id_fkey";
--;;
ALTER TABLE "OrganizationPlayers" ADD CONSTRAINT "OrganizationPlayers_user_id_fkey" 
  FOREIGN KEY (user_id) REFERENCES "Users"(id);
--;;

ALTER TABLE "password_reset_tokens" DROP CONSTRAINT IF EXISTS "password_reset_tokens_user_id_fkey";
--;;
ALTER TABLE "password_reset_tokens" ADD CONSTRAINT "password_reset_tokens_user_id_fkey" 
  FOREIGN KEY (user_id) REFERENCES "Users"(id);
--;;

ALTER TABLE "OrganizationInvitations" DROP CONSTRAINT IF EXISTS "OrganizationInvitations_invited_by_fkey";
--;;
ALTER TABLE "OrganizationInvitations" ADD CONSTRAINT "OrganizationInvitations_invited_by_fkey" 
  FOREIGN KEY (invited_by) REFERENCES "Users"(id);
--;;

ALTER TABLE "Organizations" DROP CONSTRAINT IF EXISTS "Organizations_owner_id_fkey";
--;;
ALTER TABLE "Organizations" ADD CONSTRAINT "Organizations_owner_id_fkey" 
  FOREIGN KEY (owner_id) REFERENCES "Users"(id);
--;;

ALTER TABLE "Votes" DROP CONSTRAINT IF EXISTS "Votes_voter_id_fkey";
--;;
ALTER TABLE "Votes" ADD CONSTRAINT "Votes_voter_id_fkey" 
  FOREIGN KEY (voter_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "Votes" DROP CONSTRAINT IF EXISTS "Votes_target_id_fkey";
--;;
ALTER TABLE "Votes" ADD CONSTRAINT "Votes_target_id_fkey" 
  FOREIGN KEY (target_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "Peladas" DROP CONSTRAINT IF EXISTS "Peladas_home_fixed_goalkeeper_id_fkey";
--;;
ALTER TABLE "Peladas" ADD CONSTRAINT "Peladas_home_fixed_goalkeeper_id_fkey" 
  FOREIGN KEY (home_fixed_goalkeeper_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "Peladas" DROP CONSTRAINT IF EXISTS "Peladas_away_fixed_goalkeeper_id_fkey";
--;;
ALTER TABLE "Peladas" ADD CONSTRAINT "Peladas_away_fixed_goalkeeper_id_fkey" 
  FOREIGN KEY (away_fixed_goalkeeper_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "TeamPlayers" DROP CONSTRAINT IF EXISTS "TeamPlayers_player_id_fkey";
--;;
ALTER TABLE "TeamPlayers" ADD CONSTRAINT "TeamPlayers_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "MatchSubstitutions" DROP CONSTRAINT IF EXISTS "MatchSubstitutions_out_player_id_fkey";
--;;
ALTER TABLE "MatchSubstitutions" ADD CONSTRAINT "MatchSubstitutions_out_player_id_fkey" 
  FOREIGN KEY (out_player_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "MatchSubstitutions" DROP CONSTRAINT IF EXISTS "MatchSubstitutions_in_player_id_fkey";
--;;
ALTER TABLE "MatchSubstitutions" ADD CONSTRAINT "MatchSubstitutions_in_player_id_fkey" 
  FOREIGN KEY (in_player_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "MatchEvents" DROP CONSTRAINT IF EXISTS "MatchEvents_player_id_fkey";
--;;
ALTER TABLE "MatchEvents" ADD CONSTRAINT "MatchEvents_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "Statistics" DROP CONSTRAINT IF EXISTS "Statistics_player_id_fkey";
--;;
ALTER TABLE "Statistics" ADD CONSTRAINT "Statistics_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "Attendance" DROP CONSTRAINT IF EXISTS "Attendance_player_id_fkey";
--;;
ALTER TABLE "Attendance" ADD CONSTRAINT "Attendance_player_id_fkey" 
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id);
--;;

ALTER TABLE "Attendance" DROP CONSTRAINT IF EXISTS "Attendance_pelada_id_fkey";
--;;
ALTER TABLE "Attendance" ADD CONSTRAINT "Attendance_pelada_id_fkey" 
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id);
