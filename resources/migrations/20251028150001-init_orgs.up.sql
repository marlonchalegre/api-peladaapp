CREATE TABLE IF NOT EXISTS "Organizations" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR,
  owner_id UUID REFERENCES "Users"(id)
);
