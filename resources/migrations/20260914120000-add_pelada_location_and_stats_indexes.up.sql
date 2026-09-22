ALTER TABLE "Peladas" ADD COLUMN IF NOT EXISTS location TEXT;
--;;
CREATE INDEX IF NOT EXISTS peladas_index_org_status_scheduled ON "Peladas" (organization_id, status, scheduled_at DESC);
--;;
CREATE INDEX IF NOT EXISTS matches_index_pelada_status ON "Matches" (pelada_id, status);
