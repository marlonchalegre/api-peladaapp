DROP INDEX IF EXISTS matches_index_pelada_status;
--;;
DROP INDEX IF EXISTS peladas_index_org_status_scheduled;
--;;
ALTER TABLE "Peladas" DROP COLUMN IF EXISTS location;
