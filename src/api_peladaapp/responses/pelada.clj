(ns api-peladaapp.responses.pelada
  (:require
   [schema.core :as s]))

(s/defschema PeladaResponse
  {:id s/Int
   :organization_id s/Int
   (s/optional-key :organization_name) s/Str
   (s/optional-key :scheduled_at) (s/maybe s/Any)
   (s/optional-key :num_teams) (s/maybe s/Int)
   (s/optional-key :players_per_team) (s/maybe s/Int)
   (s/optional-key :fixed_goalkeepers) (s/maybe s/Bool)
   (s/optional-key :home_fixed_goalkeeper_id) (s/maybe s/Int)
   (s/optional-key :away_fixed_goalkeeper_id) (s/maybe s/Int)
   (s/optional-key :status) (s/maybe s/Str)
   (s/optional-key :closed_at) (s/maybe s/Any)
   (s/optional-key :is_admin) s/Bool
   (s/optional-key :timer_started_at) (s/maybe s/Any)
   (s/optional-key :timer_accumulated_ms) (s/maybe s/Int)
   (s/optional-key :timer_status) (s/maybe s/Str)})

(s/defschema PeladaBeginResponse
  {:matches_created s/Int})

(s/defschema TeamLineupSchema
  {s/Int [s/Any]})

(s/defschema PeladaDashboardResponse
  {:pelada PeladaResponse
   :matches [s/Any]
   :teams [s/Any]
   :users [s/Any]
   :organization_players [s/Any]
   :match_events [s/Any]
   :player_stats (s/maybe [s/Any])
   :team_players_map {s/Int [s/Any]}
   :match_lineups_map {s/Int TeamLineupSchema}
   (s/optional-key :pelada_transactions) [s/Any]
   (s/optional-key :attendance) [s/Any]})

(s/defschema PeladaFullDetailsResponse
  {s/Any s/Any}) ;; Keeping loose for now as it aggregates a lot
