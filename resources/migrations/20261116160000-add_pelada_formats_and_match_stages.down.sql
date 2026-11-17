ALTER TABLE "PeladaMatchPlans" DROP COLUMN IF EXISTS stage;
--;;
ALTER TABLE "Matches" DROP COLUMN IF EXISTS winner_team_id;
--;;
ALTER TABLE "Matches" DROP COLUMN IF EXISTS stage;
--;;
ALTER TABLE "Peladas" DROP COLUMN IF EXISTS format_config;
--;;
ALTER TABLE "Peladas" DROP COLUMN IF EXISTS format;
