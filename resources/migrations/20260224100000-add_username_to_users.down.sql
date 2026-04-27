DROP INDEX IF EXISTS "Users_index_username";
--;;
-- SQLite doesn't support DROP COLUMN in older versions easily, 
-- but we can at least try or just leave it since it's a minor change.
-- However, for the sake of completeness:
-- ALTER TABLE Users DROP COLUMN username;
