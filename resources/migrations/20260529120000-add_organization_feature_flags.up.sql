CREATE TABLE IF NOT EXISTS "OrganizationFeatureFlags" (
  organization_id UUID PRIMARY KEY REFERENCES "Organizations"(id) ON DELETE CASCADE,
  finance_control BOOLEAN NOT NULL DEFAULT FALSE,
  waha_communications BOOLEAN NOT NULL DEFAULT FALSE,
  player_characteristics BOOLEAN NOT NULL DEFAULT FALSE,
  monthly_substitutions BOOLEAN NOT NULL DEFAULT FALSE,
  org_statistics BOOLEAN NOT NULL DEFAULT FALSE,
  peer_voting BOOLEAN NOT NULL DEFAULT FALSE,
  unlimited_members BOOLEAN NOT NULL DEFAULT FALSE,
  unlimited_peladas BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
--;;
INSERT INTO "OrganizationFeatureFlags" (
  organization_id,
  finance_control,
  waha_communications,
  player_characteristics,
  monthly_substitutions,
  org_statistics,
  peer_voting,
  unlimited_members,
  unlimited_peladas
)
SELECT 
  id,
  TRUE,
  TRUE,
  TRUE,
  TRUE,
  TRUE,
  TRUE,
  TRUE,
  TRUE
FROM "Organizations"
ON CONFLICT (organization_id) DO NOTHING;
