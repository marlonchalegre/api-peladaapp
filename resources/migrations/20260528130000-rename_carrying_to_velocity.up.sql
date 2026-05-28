ALTER TABLE "OrganizationPlayers" RENAME COLUMN "carrying" TO "velocity";
--;;
ALTER TABLE "OrganizationPlayers" DROP CONSTRAINT IF EXISTS chk_carrying;
--;;
ALTER TABLE "OrganizationPlayers" ADD CONSTRAINT chk_velocity CHECK (velocity >= 0 AND velocity <= 5);
