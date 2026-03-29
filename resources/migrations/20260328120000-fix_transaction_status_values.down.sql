-- Just a simple reversal of the status column
UPDATE "Transactions" SET "status" = 'active' WHERE "status" = 'paid';
