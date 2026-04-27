(ns api-peladaapp.db.schedule
  (:require
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(s/defn list-match-plans-by-pelada [pelada-id db]
  (->> (sql/find-by-keys db "PeladaMatchPlans" {:pelada_id pelada-id})
       (sort-by :sequence)))

(s/defn delete-match-plans-by-pelada [pelada-id db]
  (sql/delete! db "PeladaMatchPlans" {:pelada_id pelada-id}))

(s/defn insert-match-plan [{:keys [pelada-id home-team-id away-team-id sequence]} db]
  (sql/insert! db "PeladaMatchPlans" {:pelada_id pelada-id
                                      :home_team_id home-team-id
                                      :away_team_id away-team-id
                                      :sequence sequence}))
