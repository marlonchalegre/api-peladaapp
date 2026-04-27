CREATE TABLE PeladaReminders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pelada_id INTEGER NOT NULL,
    type VARCHAR NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (pelada_id) REFERENCES Peladas(id)
);
--;;

CREATE INDEX IF NOT EXISTS "PeladaReminders_idx_pelada_type" ON "PeladaReminders" ("pelada_id", "type");
--;;

-- Backfill existing reminders to avoid duplicate notifications after migration
INSERT INTO PeladaReminders (pelada_id, type, sent_at)
SELECT id, 'vote_30m', CURRENT_TIMESTAMP FROM Peladas WHERE vote_reminder_30m_sent = 1;
--;;

INSERT INTO PeladaReminders (pelada_id, type, sent_at)
SELECT id, 'vote_12h', CURRENT_TIMESTAMP FROM Peladas WHERE vote_reminder_12h_sent = 1;
--;;

INSERT INTO PeladaReminders (pelada_id, type, sent_at)
SELECT id, 'vote_23h', CURRENT_TIMESTAMP FROM Peladas WHERE vote_reminder_23h_sent = 1;
--;;

INSERT INTO PeladaReminders (pelada_id, type, sent_at)
SELECT id, 'vote_ended', CURRENT_TIMESTAMP FROM Peladas WHERE vote_ended_message_sent = 1;
--;;

INSERT INTO PeladaReminders (pelada_id, type, sent_at)
SELECT id, 'attendance', last_attendance_reminder_at FROM Peladas WHERE last_attendance_reminder_at IS NOT NULL;
