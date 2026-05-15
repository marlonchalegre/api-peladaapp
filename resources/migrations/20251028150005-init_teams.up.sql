CREATE TABLE IF NOT EXISTS "Teams" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  pelada_id UUID NOT NULL,
  name VARCHAR,
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id)
);
--;;
CREATE INDEX IF NOT EXISTS teams_index_pelada ON "Teams" (pelada_id);
