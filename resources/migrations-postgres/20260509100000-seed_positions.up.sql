INSERT INTO "Positions" (id, value) VALUES (1, 'Goalkeeper'), (2, 'Defender'), (3, 'Midfielder'), (4, 'Striker')
ON CONFLICT (id) DO NOTHING;
--;;
SELECT setval(pg_get_serial_sequence('"Positions"', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM "Positions"), 0), 1));
