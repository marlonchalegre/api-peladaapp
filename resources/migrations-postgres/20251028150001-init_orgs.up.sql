CREATE TABLE IF NOT EXISTS "Organizations" (
  id SERIAL PRIMARY KEY,
  name VARCHAR,
  owner_id INTEGER REFERENCES "Users"(id)
);
