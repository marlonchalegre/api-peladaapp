ALTER TABLE "MonthlyPayments" ADD COLUMN "fine_transaction_id" INTEGER REFERENCES "Transactions"("id") ON DELETE SET NULL;
