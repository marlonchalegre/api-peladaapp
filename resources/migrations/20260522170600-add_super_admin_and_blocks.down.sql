ALTER TABLE "Users" DROP COLUMN IF EXISTS is_super_admin;
--;;
ALTER TABLE "Users" DROP COLUMN IF EXISTS is_blocked;
--;;
ALTER TABLE "Users" DROP COLUMN IF EXISTS allow_org_creation;
--;;
ALTER TABLE "Organizations" DROP COLUMN IF EXISTS is_blocked;
