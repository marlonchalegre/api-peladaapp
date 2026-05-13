CREATE TABLE IF NOT EXISTS "Positions" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  value VARCHAR
);
