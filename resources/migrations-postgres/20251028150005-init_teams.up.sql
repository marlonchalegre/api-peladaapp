CREATE TABLE IF NOT EXISTS "Teams" (
  id SERIAL PRIMARY KEY,
  pelada_id INTEGER NOT NULL,
  name VARCHAR,
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id)
);
--;;
CREATE INDEX IF NOT EXISTS teams_index_pelada ON "Teams" (pelada_id);
--;;
