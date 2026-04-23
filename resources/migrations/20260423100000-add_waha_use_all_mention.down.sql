-- SQLite does not support DROP COLUMN before 3.35.0. 
-- For simplicity:
ALTER TABLE OrganizationWahaConfigs DROP COLUMN use_all_mention_fallback;
