ALTER TABLE "Users" ALTER COLUMN allow_org_creation SET DEFAULT FALSE;
--;;
UPDATE "Users" SET allow_org_creation = FALSE WHERE is_super_admin = FALSE OR is_super_admin IS NULL;
