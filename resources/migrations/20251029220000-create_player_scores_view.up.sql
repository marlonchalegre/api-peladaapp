CREATE VIEW IF NOT EXISTS player_scores AS
SELECT target_id AS player_id, AVG(stars) AS score
FROM "Votes"
GROUP BY target_id;