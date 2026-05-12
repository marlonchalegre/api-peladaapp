CREATE TABLE IF NOT EXISTS "OrganizationWahaConfigs" (
    id SERIAL PRIMARY KEY,
    organization_id INTEGER NOT NULL UNIQUE,
    api_url TEXT,
    instance TEXT,
    group_id TEXT,
    enabled BOOLEAN DEFAULT FALSE,
    start_msg_enabled BOOLEAN DEFAULT FALSE,
    end_msg_enabled BOOLEAN DEFAULT FALSE,
    attendance_reminder_enabled BOOLEAN DEFAULT FALSE,
    vote_reminder_enabled BOOLEAN DEFAULT FALSE,
    vote_ended_msg_enabled BOOLEAN DEFAULT FALSE,
    use_all_mention BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE
);
--;;
ALTER TABLE "Peladas" ADD COLUMN IF NOT EXISTS vote_ended_message_sent BOOLEAN DEFAULT FALSE;
