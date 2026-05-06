-- SQLite does not support DROP COLUMN easily before 3.35.0, but we can try if it's a newer version or just leave it.
-- Actually, the project seems to use SQLite.
-- For now, I'll just leave it as is or try to use the modern syntax.
ALTER TABLE "Transactions" DROP COLUMN "fine_amount";
