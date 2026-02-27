ALTER TABLE "Peladas" ADD COLUMN "fixed_goalkeepers" BOOLEAN DEFAULT 0;
--;;
ALTER TABLE "Peladas" ADD COLUMN "home_fixed_goalkeeper_id" INTEGER REFERENCES "OrganizationPlayers"("id");
--;;
ALTER TABLE "Peladas" ADD COLUMN "away_fixed_goalkeeper_id" INTEGER REFERENCES "OrganizationPlayers"("id");
--;;
ALTER TABLE "MatchLineups" ADD COLUMN "is_goalkeeper" BOOLEAN DEFAULT 0;
