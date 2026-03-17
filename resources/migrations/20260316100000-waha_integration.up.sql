-- Create OrganizationWahaConfigs table
CREATE TABLE OrganizationWahaConfigs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    organization_id INTEGER NOT NULL UNIQUE,
    api_url TEXT,
    instance TEXT,
    group_id TEXT,
    enabled BOOLEAN DEFAULT 0,
    start_msg_enabled BOOLEAN DEFAULT 0,
    end_msg_enabled BOOLEAN DEFAULT 0,
    attendance_reminder_enabled BOOLEAN DEFAULT 0,
    vote_reminder_enabled BOOLEAN DEFAULT 0,
    vote_ended_msg_enabled BOOLEAN DEFAULT 0,
    FOREIGN KEY (organization_id) REFERENCES Organizations(id) ON DELETE CASCADE
);

-- Add tracking for vote ended message to Peladas table
ALTER TABLE Peladas ADD COLUMN vote_ended_message_sent BOOLEAN DEFAULT 0;
