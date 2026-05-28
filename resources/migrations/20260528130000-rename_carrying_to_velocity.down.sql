ALTER TABLE "OrganizationPlayers" RENAME COLUMN "velocity" TO "carrying";
--;;
ALTER TABLE "OrganizationPlayers" DROP CONSTRAINT IF EXISTS chk_velocity;
--;;
ALTER TABLE "OrganizationPlayers" ADD CONSTRAINT chk_carrying CHECK (carrying >= 0 AND carrying <= 5);
