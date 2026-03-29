CREATE TABLE IF NOT EXISTS "OrganizationFinances" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "organization_id" INTEGER NOT NULL UNIQUE,
  "mensalista_price" DECIMAL(10, 2) DEFAULT 0.00,
  "diarista_price" DECIMAL(10, 2) DEFAULT 0.00,
  "currency" VARCHAR DEFAULT 'BRL',
  FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id") ON DELETE CASCADE
);

--;;

CREATE TABLE IF NOT EXISTS "Transactions" (
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
  FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id") ON DELETE CASCADE,
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id") ON DELETE SET NULL,
  FOREIGN KEY ("pelada_id") REFERENCES "Peladas"("id") ON DELETE SET NULL
);

--;;

CREATE TABLE IF NOT EXISTS "MonthlyPayments" (
  "id" INTEGER PRIMARY KEY AUTOINCREMENT,
  "organization_id" INTEGER NOT NULL,
  "player_id" INTEGER NOT NULL,
  "year" INTEGER NOT NULL,
  "month" INTEGER NOT NULL,
  "transaction_id" INTEGER,
  "paid" BOOLEAN DEFAULT 0,
  UNIQUE ("organization_id", "player_id", "year", "month"),
  FOREIGN KEY ("organization_id") REFERENCES "Organizations"("id") ON DELETE CASCADE,
  FOREIGN KEY ("player_id") REFERENCES "OrganizationPlayers"("id") ON DELETE CASCADE,
  FOREIGN KEY ("transaction_id") REFERENCES "Transactions"("id") ON DELETE SET NULL
);

--;;

CREATE INDEX IF NOT EXISTS "Transactions_index_org" ON "Transactions" ("organization_id");
CREATE INDEX IF NOT EXISTS "MonthlyPayments_index_org_player" ON "MonthlyPayments" ("organization_id", "player_id");
