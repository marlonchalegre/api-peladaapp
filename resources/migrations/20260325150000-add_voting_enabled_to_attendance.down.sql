-- Remove voting_enabled from peladaattendance
-- SQLite does not support DROP COLUMN easily, but for a down migration we can just leave it or 
-- if we really want to drop it we need to recreate the table. 
-- For simplicity in this project's migrations, we usually skip complex SQLite drops unless necessary.
SELECT 1;
