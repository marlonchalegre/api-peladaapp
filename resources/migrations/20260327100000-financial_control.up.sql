CREATE TABLE IF NOT EXISTS "OrganizationFinances" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL UNIQUE,
  mensalista_price DECIMAL(10, 2) DEFAULT 0.00,
  diarista_price DECIMAL(10, 2) DEFAULT 0.00,
  currency VARCHAR DEFAULT 'BRL',
  monthly_fine_amount DECIMAL(10, 2) DEFAULT 0.00,
  monthly_cut_off_day INTEGER DEFAULT 5,
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE
);

--;;

CREATE TABLE IF NOT EXISTS "Transactions" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL,
  player_id UUID,
  pelada_id UUID,
  amount DECIMAL(10, 2) NOT NULL,
  type VARCHAR NOT NULL CHECK (type IN ('income', 'expense')),
  category VARCHAR NOT NULL,
  description TEXT,
  payment_date DATE DEFAULT CURRENT_DATE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_by UUID REFERENCES "Users"(id) ON DELETE SET NULL,
  status VARCHAR DEFAULT 'paid' CHECK (status IN ('paid', 'reversed')),
  fine_amount DECIMAL(10, 2) DEFAULT 0.00,
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE SET NULL,
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE SET NULL
);

--;;

CREATE TABLE IF NOT EXISTS "MonthlyPayments" (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL,
  player_id UUID NOT NULL,
  year INTEGER NOT NULL,
  month INTEGER NOT NULL,
  transaction_id UUID,
  paid BOOLEAN DEFAULT FALSE,
  fine_transaction_id UUID REFERENCES "Transactions"(id) ON DELETE SET NULL,
  UNIQUE (organization_id, player_id, year, month),
  FOREIGN KEY (organization_id) REFERENCES "Organizations"(id) ON DELETE CASCADE,
  FOREIGN KEY (player_id) REFERENCES "OrganizationPlayers"(id) ON DELETE CASCADE,
  FOREIGN KEY (transaction_id) REFERENCES "Transactions"(id) ON DELETE SET NULL
);

--;;

CREATE INDEX IF NOT EXISTS transactions_index_org ON "Transactions" (organization_id);
--;;
CREATE INDEX IF NOT EXISTS monthlypayments_index_org_player ON "MonthlyPayments" (organization_id, player_id);
