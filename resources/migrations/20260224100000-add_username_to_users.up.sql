ALTER TABLE Users ADD COLUMN username VARCHAR;
--;;
CREATE UNIQUE INDEX IF NOT EXISTS "Users_index_username" ON "Users" ("username");
--;;

-- Initialize username for existing users
UPDATE Users SET username = email;
