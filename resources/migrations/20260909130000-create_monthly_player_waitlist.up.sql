CREATE TABLE IF NOT EXISTS "MonthlyPlayerWaitlist" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL,
  player_id UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE,
  CONSTRAINT unique_org_player_waitlist UNIQUE (organization_id, player_id)
);
--;;
CREATE INDEX IF NOT EXISTS waitlist_index_org_created ON "MonthlyPlayerWaitlist" (organization_id, created_at ASC);
