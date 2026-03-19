-- SQLite does not support dropping columns easily before 3.35.0
-- But we can just leave it or use a complex migration if needed.
-- For now, we just skip it as it's a new column.
ALTER TABLE Organizations DROP COLUMN owner_id;
