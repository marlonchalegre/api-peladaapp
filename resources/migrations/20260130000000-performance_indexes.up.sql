-- Performance indexes for "Peladas"
CREATE INDEX IF NOT EXISTS "Peladas_org_sched" ON "Peladas" ("organization_id", "scheduled_at");
CREATE INDEX IF NOT EXISTS "Peladas_status_closed" ON "Peladas" ("status", "closed_at");
