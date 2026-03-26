-- Add voting_enabled to peladaattendance
ALTER TABLE "peladaattendance" ADD COLUMN "voting_enabled" BOOLEAN DEFAULT 1;
