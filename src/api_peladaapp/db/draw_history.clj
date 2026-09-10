(ns api-peladaapp.db.draw-history
  "Raw reads that feed the historical signals used by the team draw algorithms.

   Every query is scoped to one organization and to peladas closed strictly
   before the draw date, so a draw never learns from the night it is arranging."
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(defn- in-range
  [organization-id before]
  [:and
   [:= :organization_id organization-id]
   [:= :status [:cast "closed" :pelada_status]]
   [:!= :scheduled_at nil]
   [:< :scheduled_at [[:cast before :timestamp]]]])

(defn- closed-peladas-before
  "Single-column subquery, so it can sit on the right of an IN."
  [organization-id before]
  (-> (h/select :id)
      (h/from :Peladas)
      (h/where (in-range organization-id before))))

(s/defn list-closed-peladas :- [s/Any]
  "Closed peladas of the organization before the draw date, oldest first.
   The order matters: title droughts are counted in chronological sequence."
  [organization-id :- s/Uuid before :- s/Str db]
  (let [query (-> (h/select :id :scheduled_at)
                  (h/from :Peladas)
                  (h/where (in-range organization-id before))
                  (h/order-by [:scheduled_at :asc] [:id :asc]))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn list-team-rosters :- [s/Any]
  "Field players registered on each team of those peladas (goalkeepers excluded)."
  [organization-id :- s/Uuid before :- s/Str db]
  (let [query (-> (h/select [:t.pelada_id :pelada_id] [:t.id :team_id] [:tp.player_id :player_id])
                  (h/from [:Teams :t])
                  (h/join [:TeamPlayers :tp] [:= :tp.team_id :t.id])
                  (h/where [:and
                            [:in :t.pelada_id (closed-peladas-before organization-id before)]
                            [:= [:coalesce :tp.is_goalkeeper false] false]]))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn list-finished-matches :- [s/Any]
  [organization-id :- s/Uuid before :- s/Str db]
  (let [query (-> (h/select :id :pelada_id :home_team_id :away_team_id :home_score :away_score)
                  (h/from :Matches)
                  (h/where [:and
                            [:in :pelada_id (closed-peladas-before organization-id before)]
                            [:= :status [:cast "finished" :match_status]]]))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn count-unfinished-matches-by-pelada :- [s/Any]
  "Peladas that still hold a match without a result. Their title is not awarded."
  [organization-id :- s/Uuid before :- s/Str db]
  (let [query (-> (h/select :pelada_id [[:count :*] :count])
                  (h/from :Matches)
                  (h/where [:and
                            [:in :pelada_id (closed-peladas-before organization-id before)]
                            [:!= :status [:cast "finished" :match_status]]])
                  (h/group-by :pelada_id))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn list-match-lineups :- [s/Any]
  "Per-match field lineups, used for on-pitch pairings and assist validation."
  [organization-id :- s/Uuid before :- s/Str db]
  (let [query (-> (h/select [:ml.match_id :match_id] [:ml.team_id :team_id] [:ml.player_id :player_id])
                  (h/from [:MatchLineups :ml])
                  (h/join [:Matches :m] [:= :m.id :ml.match_id])
                  (h/where [:and
                            [:in :m.pelada_id (closed-peladas-before organization-id before)]
                            [:= :m.status [:cast "finished" :match_status]]
                            [:= [:coalesce :ml.is_goalkeeper false] false]]))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn list-assist-links :- [s/Any]
  "Assists joined to the goal they explicitly point at, never inferred by timing.
   Only assist events carrying a parent_event_id that resolves to a goal in the
   same match are returned."
  [organization-id :- s/Uuid before :- s/Str db]
  (let [query (-> (h/select [:a.id :assist_id] [:a.match_id :match_id]
                            [:a.player_id :passer_id] [:g.player_id :scorer_id]
                            [:g.id :goal_id])
                  (h/from [:MatchEvents :a])
                  (h/join [:MatchEvents :g] [:and
                                             [:= :g.id :a.parent_event_id]
                                             [:= :g.event_type [:cast "goal" :match_event_type]]
                                             [:= :g.match_id :a.match_id]])
                  (h/join [:Matches :m] [:= :m.id :a.match_id])
                  (h/where [:and
                            [:in :m.pelada_id (closed-peladas-before organization-id before)]
                            [:= :m.status [:cast "finished" :match_status]]
                            [:= :a.event_type [:cast "assist" :match_event_type]]]))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn count-assist-events :- s/Int
  "All assist events in range, so coverage can report how many were discarded."
  [organization-id :- s/Uuid before :- s/Str db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from [:MatchEvents :e])
                  (h/join [:Matches :m] [:= :m.id :e.match_id])
                  (h/where [:and
                            [:in :m.pelada_id (closed-peladas-before organization-id before)]
                            [:= :m.status [:cast "finished" :match_status]]
                            [:= :e.event_type [:cast "assist" :match_event_type]]]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts) :count int)))

(s/defn list-scoring-events :- [s/Any]
  "Goal and assist tallies per player, used by the offensive index."
  [organization-id :- s/Uuid before :- s/Str db]
  (let [query (-> (h/select [:e.player_id :player_id] [:e.event_type :event_type]
                            [[:count :*] :count])
                  (h/from [:MatchEvents :e])
                  (h/join [:Matches :m] [:= :m.id :e.match_id])
                  (h/where [:and
                            [:in :m.pelada_id (closed-peladas-before organization-id before)]
                            [:= :m.status [:cast "finished" :match_status]]
                            [:in :e.event_type [[:cast "goal" :match_event_type]
                                                [:cast "assist" :match_event_type]]]])
                  (h/group-by :e.player_id :e.event_type))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn aggregate-vote-stars :- [s/Any]
  "Star total and count per player, for the Bayesian rating. Aggregated in SQL:
   a mature organization holds tens of thousands of individual votes and the
   rating only ever needs these two numbers per player."
  [organization-id :- s/Uuid before :- s/Str db]
  (let [query (-> (h/select [:v.target_id :player_id]
                            [[:sum :v.stars] :total] [[:count :v.stars] :count])
                  (h/from [:Votes :v])
                  (h/join [:Peladas :p] [:= :p.id :v.pelada_id])
                  (h/where [:and
                            [:= :p.organization_id organization-id]
                            [:or [:= :p.scheduled_at nil]
                             [:< :p.scheduled_at [[:cast before :timestamp]]]]])
                  (h/group-by :v.target_id))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))
