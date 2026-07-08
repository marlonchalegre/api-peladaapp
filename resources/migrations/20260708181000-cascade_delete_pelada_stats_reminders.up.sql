ALTER TABLE "Statistics" DROP CONSTRAINT IF EXISTS "Statistics_pelada_id_fkey";
--;;
ALTER TABLE "Statistics" ADD CONSTRAINT "Statistics_pelada_id_fkey" 
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE CASCADE;
--;;
ALTER TABLE "PeladaReminders" DROP CONSTRAINT IF EXISTS "PeladaReminders_pelada_id_fkey";
--;;
ALTER TABLE "PeladaReminders" ADD CONSTRAINT "PeladaReminders_pelada_id_fkey" 
  FOREIGN KEY (pelada_id) REFERENCES "Peladas"(id) ON DELETE CASCADE;
