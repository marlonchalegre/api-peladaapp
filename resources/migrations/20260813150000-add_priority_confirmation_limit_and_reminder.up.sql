ALTER TABLE "Organizations" ADD COLUMN IF NOT EXISTS priority_confirmation_limit_hours INTEGER DEFAULT NULL;
--;;
ALTER TYPE reminder_type ADD VALUE IF NOT EXISTS 'priority_ending';
