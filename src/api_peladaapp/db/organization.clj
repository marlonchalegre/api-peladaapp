(ns api-peladaapp.db.organization
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [api-peladaapp.helpers.sql :as hsql]
   [clojure.string :as str]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-organization :- s/Int
  [org db]
  (let [row (adapter.organization/model->db org)
        query (-> (h/insert-into :Organizations)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn get-organization [id db]
  (let [query (-> (h/select :o.*
                            [:wc.api_url :waha_api_url]
                            [:wc.instance :waha_instance]
                            [:wc.group_id :waha_group_id]
                            [:wc.enabled :waha_enabled]
                            [:wc.start_msg_enabled :waha_start_msg_enabled]
                            [:wc.end_msg_enabled :waha_end_msg_enabled]
                            [:wc.attendance_reminder_enabled :waha_attendance_reminder_enabled]
                            [:wc.vote_reminder_enabled :waha_vote_reminder_enabled]
                            [:wc.vote_ended_msg_enabled :waha_vote_ended_msg_enabled]
                            [:wc.use_all_mention :waha_use_all_mention])
                  (h/from [:Organizations :o])
                  (h/left-join [:OrganizationWahaConfigs :wc] [:= :o.id :wc.organization_id])
                  (h/where [:= :o.id id]))
        result (jdbc/execute! db (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})]
    (some-> result first adapter.organization/db->model)))

(s/defn update-organization :- s/Int
  [id org db]
  (jdbc/with-transaction [tx db]
    (let [org-row (select-keys org [:name])
          waha-row (-> org
                       (select-keys [:waha-api-url :waha-instance :waha-group-id :waha-enabled :waha-start-msg-enabled :waha-end-msg-enabled :waha-attendance-reminder-enabled :waha-vote-reminder-enabled :waha-vote-ended-msg-enabled :waha-use-all-mention])
                       (update-keys (comp keyword #(str/replace % "waha-" "") name))
                       (update-keys (comp keyword #(str/replace % "-" "_") name)))]

      (when (seq org-row)
        (let [query (-> (h/update :Organizations)
                        (h/set org-row)
                        (h/where [:= :id id]))]
          (jdbc/execute! tx (hsql/format query))))

      (when (seq waha-row)
        (let [exists-query (-> (h/select 1)
                               (h/from :OrganizationWahaConfigs)
                               (h/where [:= :organization_id id]))
              exists? (jdbc/execute-one! tx (hsql/format exists-query))]
          (if exists?
            (let [query (-> (h/update :OrganizationWahaConfigs)
                            (h/set waha-row)
                            (h/where [:= :organization_id id]))]
              (jdbc/execute! tx (hsql/format query)))
            (let [query (-> (h/insert-into :OrganizationWahaConfigs)
                            (h/values [(assoc waha-row :organization_id id)]))]
              (jdbc/execute! tx (hsql/format query))))))
      1)))

(s/defn delete-organization :- s/Int
  [id db]
  (let [query (-> (h/delete-from :Organizations)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn list-organizations [db per-page offset]
  (let [query (-> (h/select :*)
                  (h/from :Organizations)
                  (h/order-by [:id :asc])
                  (h/limit per-page)
                  (h/offset offset))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.organization/db->model))))

(s/defn count-organizations [db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :Organizations))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        :count
        int)))

(s/defn list-all-organizations [db]
  (let [query (-> (h/select :*)
                  (h/from :Organizations))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.organization/db->model))))

(s/defn list-by-user [user-id db]
  (let [query (-> (h/select :id :name :role)
                  (h/from [(h/union
                            (-> (h/select :o.id :o.name [[:raw "'admin'"] :role] [1 :priority])
                                (h/from [:Organizations :o])
                                (h/join [:OrganizationAdmins :oa] [:= :oa.organization_id :o.id])
                                (h/where [:= :oa.user_id user-id]))
                            (-> (h/select :o.id :o.name [[:raw "'player'"] :role] [2 :priority])
                                (h/from [:Organizations :o])
                                (h/join [:OrganizationPlayers :op] [:= :op.organization_id :o.id])
                                (h/where [:= :op.user_id user-id])))
                           :orgs])
                  (h/order-by :id :priority))]
    (->> (jdbc/execute! db (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})
         (group-by :id)
         (map (fn [[_ orgs]] (first orgs)))
         (map adapter.organization/db->model))))

(s/defn get-statistics
  [id :- s/Int
   year :- s/Int
   db]
  (let [where-year (if (pos? year) [[:= [:to_char :p.scheduled_at "YYYY"] (str year)]] [])
        raw-participation (h/union
                           (-> (h/select :ml.player_id :m.pelada_id)
                               (h/from [:matchlineups :ml])
                               (h/join [:Matches :m] [:= :ml.match_id :m.id])
                               (h/join [:Peladas :p] [:= :m.pelada_id :p.id])
                               (h/where (into [:and [:= :p.organization_id id]] where-year)))
                           (-> (h/select :tp.player_id :m.pelada_id)
                               (h/from [:TeamPlayers :tp])
                               (h/join [:Teams :t] [:= :tp.team_id :t.id])
                               (h/join [:Matches :m] [:or [:= :m.home_team_id :t.id] [:= :m.away_team_id :t.id]])
                               (h/join [:Peladas :p] [:= :m.pelada_id :p.id])
                               (h/where (into [:and [:= :p.organization_id id] [:not-exists (-> (h/select 1)
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
                           (h/where (into [:and [:= :p.organization_id id]] where-year))
                           (h/group-by :me.player_id :me.event_type))
                       (-> (h/select :ms.player_id [[:raw "'goal'"] :event_type] [:ms.goals :event_count])
                           (h/from [:ManualStats :ms])
                           (h/where (cond-> [:and [:= :ms.organization_id id] [:> :ms.goals 0]]
                                      (pos? year) (conj [:= :ms.year year]))))
                       (-> (h/select :ms.player_id [[:raw "'assist'"] :event_type] [:ms.assists :event_count])
                           (h/from [:ManualStats :ms])
                           (h/where (cond-> [:and [:= :ms.organization_id id] [:> :ms.assists 0]]
                                      (pos? year) (conj [:= :ms.year year]))))
                       (-> (h/select :ms.player_id [[:raw "'own_goal'"] :event_type] [:ms.own_goals :event_count])
                           (h/from [:ManualStats :ms])
                           (h/where (cond-> [:and [:= :ms.organization_id id] [:> :ms.own_goals 0]]
                                      (pos? year) (conj [:= :ms.year year])))))
        all-players (h/union
                     (-> (h/select :player_id) (h/from :PlayerParticipation))
                     (-> (h/select :player_id) (h/from :ManualStats) (h/where (cond-> [:and [:= :organization_id id]] (pos? year) (conj [:= :year year])))))
        player-ratings (-> (h/select [:v.target_id :player_id] [[:avg :v.stars] :avg_rating])
                           (h/from [:Votes :v])
                           (h/join [:Peladas :p] [:= :v.pelada_id :p.id])
                           (h/where (into [:and [:= :p.organization_id id]] where-year))
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
