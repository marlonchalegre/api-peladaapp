(ns api-peladaapp.db.organization
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [medley.core :as medley.core]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-organization :- s/Uuid
  [{:keys [name owner-id]} :- {:name s/Str :owner-id (s/maybe s/Uuid)}
   db]
  (let [query (-> (h/insert-into :Organizations)
                  (h/values [{:name name :owner_id owner-id}])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn update-organization :- s/Int
  [id :- s/Uuid
   organization
   db]
  (jdbc/with-transaction [tx db]
    (let [org-row (medley.core/assoc-some {}
                                          :name (:name organization)
                                          :owner_id (:owner-id organization))
          _ (when (seq org-row)
              (jdbc/execute! tx (hsql/format (-> (h/update :Organizations)
                                                 (h/set org-row)
                                                 (h/where [:= :id id])))))
          waha-row (medley.core/assoc-some {}
                                           :api_url (:waha-api-url organization)
                                           :instance (:waha-instance organization)
                                           :group_id (:waha-group-id organization)
                                           :enabled (:waha-enabled organization)
                                           :start_msg_enabled (:waha-start-msg-enabled organization)
                                           :end_msg_enabled (:waha-end-msg-enabled organization)
                                           :attendance_reminder_enabled (:waha-attendance-reminder-enabled organization)
                                           :vote_reminder_enabled (:waha-vote-reminder-enabled organization)
                                           :vote_ended_msg_enabled (:waha-vote-ended-msg-enabled organization)
                                           :use_all_mention (:waha-use-all-mention organization))]
      (when (seq waha-row)
        (let [exists? (jdbc/execute-one! tx (hsql/format (-> (h/select 1)
                                                             (h/from :OrganizationWahaConfigs)
                                                             (h/where [:= :organization_id id]))))]
          (if exists?
            (jdbc/execute! tx (hsql/format (-> (h/update :OrganizationWahaConfigs)
                                               (h/set waha-row)
                                               (h/where [:= :organization_id id]))))
            (jdbc/execute! tx (hsql/format (-> (h/insert-into :OrganizationWahaConfigs)
                                               (h/values [(assoc waha-row :organization_id id)])))))))
      1)))

(s/defn delete-organization :- s/Int
  [id :- s/Uuid
   db]
  (let [query (-> (h/delete-from :Organizations)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn get-organization :- s/Any
  [id :- s/Uuid
   db]
  (let [query (-> (h/select :o.*
                            [:owc.api_url :waha_api_url]
                            [:owc.instance :waha_instance]
                            [:owc.group_id :waha_group_id]
                            [:owc.enabled :waha_enabled]
                            [:owc.start_msg_enabled :waha_start_msg_enabled]
                            [:owc.end_msg_enabled :waha_end_msg_enabled]
                            [:owc.attendance_reminder_enabled :waha_attendance_reminder_enabled]
                            [:owc.vote_reminder_enabled :waha_vote_reminder_enabled]
                            [:owc.vote_ended_msg_enabled :waha_vote_ended_msg_enabled]
                            [:owc.use_all_mention :waha_use_all_mention])
                  (h/from [:Organizations :o])
                  (h/left-join [:OrganizationWahaConfigs :owc] [:= :owc.organization_id :o.id])
                  (h/where [:= :o.id id]))]
    (some-> (jdbc/execute-one! db (hsql/format query) opts)
            adapter.organization/db->model)))

(s/defn list-organizations :- [s/Any]
  ([db] (list-organizations db 1000 0))
  ([db limit offset]
   (let [query (-> (h/select :o.*
                             [:owc.api_url :waha_api_url]
                             [:owc.instance :waha_instance]
                             [:owc.group_id :waha_group_id]
                             [:owc.enabled :waha_enabled]
                             [:owc.start_msg_enabled :waha_start_msg_enabled]
                             [:owc.end_msg_enabled :waha_end_msg_enabled]
                             [:owc.attendance_reminder_enabled :waha_attendance_reminder_enabled]
                             [:owc.vote_reminder_enabled :waha_vote_reminder_enabled]
                             [:owc.vote_ended_msg_enabled :waha_vote_ended_msg_enabled]
                             [:owc.use_all_mention :waha_use_all_mention])
                   (h/from [:Organizations :o])
                   (h/left-join [:OrganizationWahaConfigs :owc] [:= :owc.organization_id :o.id])
                   (h/order-by [:o.id :desc])
                   (h/limit limit)
                   (h/offset offset))]
     (->> (jdbc/execute! db (hsql/format query) opts)
          (map adapter.organization/db->model)))))

(s/defn count-organizations :- s/Int
  [db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :Organizations))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        :count
        int)))

(s/defn list-by-user :- [s/Any]
  [user-id :- s/Uuid
   db]
  (let [id-uuid [:cast user-id :uuid]
        query (-> (h/select :o.id :o.name [[:raw "COALESCE(oa_role.role, op_role.role)"] :role] [[:raw "COALESCE(oa_role.priority, op_role.priority)"] :priority])
                  (h/from [:Organizations :o])
                  (h/left-join [(-> (h/select :organization_id [[:raw "'admin'"] :role] [1 :priority])
                                    (h/from :OrganizationAdmins)
                                    (h/where [:= :user_id id-uuid])) :oa_role]
                               [:= :oa_role.organization_id :o.id])
                  (h/left-join [(-> (h/select :organization_id [[:raw "'player'"] :role] [2 :priority])
                                    (h/from :OrganizationPlayers)
                                    (h/where [:= :user_id id-uuid])) :op_role]
                               [:= :op_role.organization_id :o.id])
                  (h/where [:or [:!= :oa_role.role nil] [:!= :op_role.role nil]])
                  (h/order-by :o.name :priority))]
    (->> (jdbc/execute! db (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})
         (group-by :id)
         (map (fn [[_ orgs]] (first orgs)))
         (map adapter.organization/db->model))))

(s/defn get-statistics
  [id :- s/Uuid
   year :- s/Int
   db]
  (let [id-uuid [:cast id :uuid]
        where-year (if (pos? year) [[:= [:to_char :p.scheduled_at "YYYY"] (str year)]] [])
        raw-participation (h/union
                           (-> (h/select :ml.player_id :m.pelada_id)
                               (h/from [:matchlineups :ml])
                               (h/join [:Matches :m] [:= :ml.match_id :m.id])
                               (h/join [:Peladas :p] [:= :m.pelada_id :p.id])
                               (h/where (into [:and [:= :p.organization_id id-uuid]] where-year)))
                           (-> (h/select :tp.player_id :m.pelada_id)
                               (h/from [:TeamPlayers :tp])
                               (h/join [:Teams :t] [:= :tp.team_id :t.id])
                               (h/join [:Matches :m] [:or [:= :m.home_team_id :t.id] [:= :m.away_team_id :t.id]])
                               (h/join [:Peladas :p] [:= :m.pelada_id :p.id])
                               (h/where (into [:and [:= :p.organization_id id-uuid] [:not-exists (-> (h/select 1)
                                                                                                     (h/from [:matchlineups :sub_ml])
                                                                                                     (h/where [:= :sub_ml.match_id :m.id]))]] where-year))))
        player-participation (-> (h/select :player_id [[:count [:distinct :pelada_id]] :peladas_count])
                                 (h/from :RawParticipation)
                                 (h/group-by :player_id))
        player-events (h/union-all
                       (-> (h/select :me.player_id :me.event_type [[:count :*] :event_count])
                           (h/from [:MatchEvents :me])
                           (h/join [:Matches :m] [:= :me.match_id :m.id])
                           (h/join [:Peladas :p] [:= :m.pelada_id :p.id])
                           (h/where (into [:and [:= :p.organization_id id-uuid]] where-year))
                           (h/group-by :me.player_id :me.event_type))
                       (-> (h/select :ms.player_id [[:raw "'goal'"] :event_type] [:ms.goals :event_count])
                           (h/from [:ManualStats :ms])
                           (h/where (cond-> [:and [:= :ms.organization_id id-uuid] [:> :ms.goals 0]]
                                      (pos? year) (conj [:= :ms.year year]))))
                       (-> (h/select :ms.player_id [[:raw "'assist'"] :event_type] [:ms.assists :event_count])
                           (h/from [:ManualStats :ms])
                           (h/where (cond-> [:and [:= :ms.organization_id id-uuid] [:> :ms.assists 0]]
                                      (pos? year) (conj [:= :ms.year year]))))
                       (-> (h/select :ms.player_id [[:raw "'own_goal'"] :event_type] [:ms.own_goals :event_count])
                           (h/from [:ManualStats :ms])
                           (h/where (cond-> [:and [:= :ms.organization_id id-uuid] [:> :ms.own_goals 0]]
                                      (pos? year) (conj [:= :ms.year year])))))
        all-players (h/union
                     (-> (h/select :player_id) (h/from :PlayerParticipation))
                     (-> (h/select :player_id) (h/from :ManualStats) (h/where (cond-> [:and [:= :organization_id id-uuid]] (pos? year) (conj [:= :year year])))))
        player-ratings (-> (h/select [:v.target_id :player_id] [[:avg :v.stars] :avg_rating])
                           (h/from [:Votes :v])
                           (h/join [:Peladas :p] [:= :v.pelada_id :p.id])
                           (h/where (into [:and [:= :p.organization_id id-uuid]] where-year))
                           (h/group-by :v.target_id))
        final-query (-> (h/with [:RawParticipation raw-participation]
                                [:PlayerParticipation player-participation]
                                [:PlayerEvents player-events]
                                [:AllPlayers all-players]
                                [:PlayerRatings player-ratings])
                        (h/select :ap.player_id
                                  [:u.id :user_id]
                                  [:u.name :player_name]
                                  [:u.position :player_position]
                                  :u.avatar_filename
                                  [[:coalesce :pp.peladas_count 0] :peladas_count]
                                  [[:coalesce :pr.avg_rating 0.0] :avg_rating]
                                  :pe.event_type
                                  [[:sum :pe.event_count] :count])
                        (h/from [:AllPlayers :ap])
                        (h/join [:OrganizationPlayers :op] [:= :ap.player_id :op.id])
                        (h/join [:Users :u] [:= :op.user_id :u.id])
                        (h/left-join [:PlayerParticipation :pp] [:= :ap.player_id :pp.player_id])
                        (h/left-join [:PlayerEvents :pe] [:= :ap.player_id :pe.player_id])
                        (h/left-join [:PlayerRatings :pr] [:= :ap.player_id :pr.player_id])
                        (h/group-by :ap.player_id :u.id :u.name :u.position :u.avatar_filename :pp.peladas_count :pr.avg_rating :pe.event_type))]
    (jdbc/execute! db (hsql/format final-query) {:builder-fn rs/as-unqualified-lower-maps})))
