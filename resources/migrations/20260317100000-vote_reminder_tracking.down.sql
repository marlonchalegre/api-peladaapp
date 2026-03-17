-- SQLite doesn't support dropping columns directly, so we'd need to recreate the table.
-- For simple migrations like this, it's often omitted in dev unless necessary.
-- But to follow best practices:

CREATE TABLE Peladas_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    organization_id INTEGER NOT NULL,
    scheduled_at TEXT,
    num_teams INTEGER,
    players_per_team INTEGER,
    fixed_goalkeepers INTEGER DEFAULT 0,
    home_fixed_goalkeeper_id INTEGER,
    away_fixed_goalkeeper_id INTEGER,
    status TEXT,
    closed_at TEXT,
    timer_started_at TEXT,
    timer_accumulated_ms INTEGER DEFAULT 0,
    timer_status TEXT DEFAULT 'stopped',
    vote_ended_message_sent BOOLEAN DEFAULT 0,
    FOREIGN KEY (organization_id) REFERENCES Organizations(id) ON DELETE CASCADE
);

INSERT INTO Peladas_new (id, organization_id, scheduled_at, num_teams, players_per_team, fixed_goalkeepers, 
                         home_fixed_goalkeeper_id, away_fixed_goalkeeper_id, status, closed_at, 
                         timer_started_at, timer_accumulated_ms, timer_status, vote_ended_message_sent)
SELECT id, organization_id, scheduled_at, num_teams, players_per_team, fixed_goalkeepers, 
       home_fixed_goalkeeper_id, away_fixed_goalkeeper_id, status, closed_at, 
       timer_started_at, timer_accumulated_ms, timer_status, vote_ended_message_sent
FROM Peladas;

DROP TABLE Peladas;
ALTER TABLE Peladas_new RENAME TO Peladas;
