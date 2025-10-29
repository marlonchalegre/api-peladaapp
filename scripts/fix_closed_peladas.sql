-- Fix closed peladas that don't have closed_at timestamp
-- This can happen if peladas were closed before the closed_at column was added

UPDATE "Peladas"
SET "closed_at" = COALESCE("scheduled_at", CURRENT_TIMESTAMP)
WHERE "status" = 'closed' AND "closed_at" IS NULL;

-- Fix incomplete timestamp formats (add seconds and timezone if missing)
UPDATE "Peladas"
SET "closed_at" = "closed_at" || ':00.000Z'
WHERE "status" = 'closed' AND "closed_at" NOT LIKE '%Z';
