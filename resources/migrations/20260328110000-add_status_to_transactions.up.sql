ALTER TABLE "Transactions" ADD COLUMN "status" VARCHAR DEFAULT 'active' CHECK ("status" IN ('active', 'reversed'));
