-- 1. Remove duplicates, keeping the one with the highest grade (or highest ID if grades are same)
DELETE FROM OrganizationPlayers
WHERE id NOT IN (
    SELECT id
    FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY organization_id, user_id ORDER BY grade DESC, id DESC) as rn
        FROM OrganizationPlayers
    )
    WHERE rn = 1
);

-- 2. Add unique index to prevent future duplicates
CREATE UNIQUE INDEX IF NOT EXISTS "OrgPlayers_unique_org_user" ON "OrganizationPlayers" ("organization_id", "user_id");
