CREATE TABLE IF NOT EXISTS "PeladaMatchPlans" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pelada_id UUID NOT NULL,
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    sequence INTEGER NOT NULL,
    UNIQUE (pelada_id, sequence),
    FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE CASCADE,
    FOREIGN KEY (home_team_id) REFERENCES "Teams"(id) ON DELETE CASCADE,
    FOREIGN KEY (away_team_id) REFERENCES "Teams"(id) ON DELETE CASCADE
);
--;;
CREATE TABLE IF NOT EXISTS "OrganizationScheduleFormats" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    team_count INTEGER NOT NULL,
    matches_per_team INTEGER NOT NULL,
    format_data TEXT NOT NULL,
    UNIQUE (organization_id, team_count, matches_per_team),
    FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE
);
--;;
CREATE INDEX IF NOT EXISTS peladamatchplans_index_pelada ON "PeladaMatchPlans" (pelada_id);
