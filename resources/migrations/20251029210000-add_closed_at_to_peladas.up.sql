-- Add closed_at timestamp to Peladas table
ALTER TABLE "Peladas" ADD COLUMN "closed_at" TIMESTAMP;

-- Update existing closed peladas with a default closed_at timestamp
-- Using scheduled_at as a base or current time if scheduled_at is null
UPDATE "Peladas"
SET "closed_at" = COALESCE("scheduled_at", CURRENT_TIMESTAMP)
WHERE "status" = 'closed' AND "closed_at" IS NULL;
