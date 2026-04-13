UPDATE "Transactions" SET "payment_date" = date("created_at") WHERE "payment_date" IS NULL;
