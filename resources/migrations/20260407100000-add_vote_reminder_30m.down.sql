-- There is no simple ALTER TABLE DROP COLUMN in SQLite < 3.35.0,
-- but since we are using a fairly recent one we can use it.
-- If not, we'd need to recreate the table.
ALTER TABLE Peladas DROP COLUMN vote_reminder_30m_sent;
