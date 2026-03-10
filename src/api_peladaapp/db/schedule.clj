(ns api-peladaapp.db.schedule
  (:require
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(s/defn list-match-plans-by-pelada [pelada-id db]
  (->> (sql/find-by-keys db :PeladaMatchPlans {:pelada_id pelada-id})
       (sort-by :PeladaMatchPlans/sequence)))

(s/defn delete-match-plans-by-pelada [pelada-id db]
  (sql/delete! db :PeladaMatchPlans {:pelada_id pelada-id}))

(s/defn insert-match-plan [{:keys [pelada-id home-team-id away-team-id sequence]} db]
  (sql/insert! db :PeladaMatchPlans {:pelada_id pelada-id
                                     :home_team_id home-team-id
                                     :away_team_id away-team-id
                                     :sequence sequence}))

(s/defn get-format [organization-id team-count matches-per-team db]
  (sql/get-by-id db :OrganizationScheduleFormats organization-id :organization_id
                 {:team_count team-count :matches_per_team matches-per-team}))

(s/defn upsert-format [{:keys [organization-id team-count matches-per-team format-data]} db]
  (let [existing (sql/get-by-id db :OrganizationScheduleFormats organization-id :organization_id
                                {:team_count team-count :matches_per_team matches-per-team})]
    (if existing
      (sql/update! db :OrganizationScheduleFormats {:format_data format-data}
                   {:id (:OrganizationScheduleFormats/id existing)})
      (sql/insert! db :OrganizationScheduleFormats {:organization_id organization-id
                                                    :team_count team-count
                                                    :matches_per_team matches-per-team
                                                    :format_data format-data}))))

(s/defn get-organization-schedule-format [organization-id team-count matches-per-team db]
  (-> (sql/query db ["SELECT * FROM OrganizationScheduleFormats WHERE organization_id = ? AND team_count = ? AND matches_per_team = ?"
                     organization-id team-count matches-per-team])
      first))
