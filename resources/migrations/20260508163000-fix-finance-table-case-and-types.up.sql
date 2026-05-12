-- Rename lowercase finance tables to match quoted names used in SQL (e.g. "Transactions")

-- Keep statements simple to avoid driver batch issues
ALTER TABLE IF EXISTS transactions RENAME TO "Transactions";
--;;
ALTER TABLE IF EXISTS organizationfinances RENAME TO "OrganizationFinances";
--;;
ALTER TABLE IF EXISTS monthlypayments RENAME TO "MonthlyPayments";
