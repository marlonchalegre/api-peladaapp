-- Rename lowercase core tables to match quoted names used in SQL (e.g. "Users")
-- Using PL/pgSQL to make it idempotent

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'users') THEN
        ALTER TABLE users RENAME TO "Users";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'organizations') THEN
        ALTER TABLE organizations RENAME TO "Organizations";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'positions') THEN
        ALTER TABLE positions RENAME TO "Positions";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'organizationplayers') THEN
        ALTER TABLE organizationplayers RENAME TO "OrganizationPlayers";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'peladas') THEN
        ALTER TABLE peladas RENAME TO "Peladas";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'teams') THEN
        ALTER TABLE teams RENAME TO "Teams";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'teamplayers') THEN
        ALTER TABLE teamplayers RENAME TO "TeamPlayers";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'matches') THEN
        ALTER TABLE matches RENAME TO "Matches";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'matchsubstitutions') THEN
        ALTER TABLE matchsubstitutions RENAME TO "MatchSubstitutions";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'statistics') THEN
        ALTER TABLE statistics RENAME TO "Statistics";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'votes') THEN
        ALTER TABLE votes RENAME TO "Votes";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'matchevents') THEN
        ALTER TABLE matchevents RENAME TO "MatchEvents";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'organizationadmins') THEN
        ALTER TABLE organizationadmins RENAME TO "OrganizationAdmins";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'organizationinvitations') THEN
        ALTER TABLE organizationinvitations RENAME TO "OrganizationInvitations";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'peladaplayerstats') THEN
        ALTER TABLE peladaplayerstats RENAME TO "PeladaPlayerStats";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'manualstats') THEN
        ALTER TABLE manualstats RENAME TO "ManualStats";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'peladamatchplans') THEN
        ALTER TABLE peladamatchplans RENAME TO "PeladaMatchPlans";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'organizationscheduleformats') THEN
        ALTER TABLE organizationscheduleformats RENAME TO "OrganizationScheduleFormats";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'organizationwahaconfigs') THEN
        ALTER TABLE organizationwahaconfigs RENAME TO "OrganizationWahaConfigs";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'peladareminders') THEN
        ALTER TABLE peladareminders RENAME TO "PeladaReminders";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'monthlyplayersubstitutions') THEN
        ALTER TABLE monthlyplayersubstitutions RENAME TO "MonthlyPlayerSubstitutions";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'peladaattendance') THEN
        ALTER TABLE peladaattendance RENAME TO "Attendance";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'organizationfinances') THEN
        ALTER TABLE organizationfinances RENAME TO "OrganizationFinances";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'financetransactions') THEN
        ALTER TABLE financetransactions RENAME TO "FinanceTransactions";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'monthlydues') THEN
        ALTER TABLE monthlydues RENAME TO "MonthlyDues";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'transactions') THEN
        ALTER TABLE transactions RENAME TO "Transactions";
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'monthlypayments') THEN
        ALTER TABLE monthlypayments RENAME TO "MonthlyPayments";
    END IF;
END $$;
