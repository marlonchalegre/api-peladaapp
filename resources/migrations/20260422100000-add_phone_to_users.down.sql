-- SQLite does not support DROP COLUMN before 3.35.0. 
-- Since we use ALTER TABLE ADD COLUMN, we might need a more complex migration to drop it if we want to be fully reversible.
-- However, for many SQLite setups, we just leave it or use the table recreation pattern.
-- For simplicity and following existing patterns if any:
ALTER TABLE Users DROP COLUMN phone;
