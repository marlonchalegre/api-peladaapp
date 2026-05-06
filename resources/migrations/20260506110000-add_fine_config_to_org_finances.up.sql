ALTER TABLE "OrganizationFinances" ADD COLUMN "monthly_fine_amount" DECIMAL(10, 2) DEFAULT 0.00;
--;;
ALTER TABLE "OrganizationFinances" ADD COLUMN "monthly_cut_off_day" INTEGER DEFAULT 5;
