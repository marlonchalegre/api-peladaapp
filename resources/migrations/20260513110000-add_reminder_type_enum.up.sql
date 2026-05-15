CREATE TYPE reminder_type AS ENUM ('attendance', 'vote_ended', 'vote_30m', 'vote_12h', 'vote_23h');
--;;
ALTER TABLE "PeladaReminders" ALTER COLUMN type TYPE reminder_type USING type::reminder_type;
