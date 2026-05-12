CREATE EXTENSION IF NOT EXISTS hstore;--;;
-- "Users"
CREATE TABLE IF NOT EXISTS "Users" (
  id SERIAL PRIMARY KEY,
  email VARCHAR UNIQUE,
  password VARCHAR,
  name VARCHAR,
  position VARCHAR(32),
  username VARCHAR,
  avatar_filename TEXT,
  phone VARCHAR
);
--;;
CREATE INDEX IF NOT EXISTS users_index_email ON "Users" (email);
--;;
CREATE UNIQUE INDEX IF NOT EXISTS users_index_username ON "Users" (username);
