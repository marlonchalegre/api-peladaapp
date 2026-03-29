-- SQLite does not allow altering check constraints directly.
-- We must recreate the table.

-- 1. Create the new table with the new constraint
CREATE TABLE "Transactions_new" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "organization_id" INTEGER NOT NULL,
  "player_id" INTEGER,
  "pelada_id" INTEGER,
  "amount" DECIMAL(10, 2) NOT NULL,
  "type" VARCHAR NOT NULL CHECK ("type" IN ('income', 'expense')),
  "category" VARCHAR NOT NULL,
  "description" TEXT,
  "payment_date" DATE DEFAULT (date('now')),
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "created_by" INTEGER REFERENCES "Users"("id") ON DELETE SET NULL,
  "status" VARCHAR DEFAULT 'paid' CHECK ("status" IN ('paid', 'reversed')),
  FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id") ON DELETE CASCADE,
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id") ON DELETE SET NULL,
  FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id") ON DELETE SET NULL
);

--;;

-- 2. Copy data, mapping 'active' to 'paid'
INSERT INTO "Transactions_new" ("id", "organization_id", "player_id", "pelada_id", "amount", "type", "category", "description", "payment_date", "created_at", "created_by", "status")
SELECT "id", "organization_id", "player_id", "pelada_id", "amount", "type", "category", "description", "payment_date", "created_at", "created_by", 
       CASE WHEN "status" = 'active' THEN 'paid' ELSE "status" END
FROM "Transactions";

--;;

-- 3. Drop the old table
DROP TABLE "Transactions";

--;;

-- 4. Rename new table to old name
ALTER TABLE "Transactions_new" RENAME TO "Transactions";

--;;

-- 5. Recreate indexes
CREATE INDEX IF NOT EXISTS "Transactions_index_org" ON "Transactions" ("organization_id");
