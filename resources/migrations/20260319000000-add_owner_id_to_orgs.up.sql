ALTER TABLE Organizations ADD COLUMN owner_id INTEGER REFERENCES Users(id);
