(ns api-peladaapp.responses.pelada
  (:require
   [api-peladaapp.models.user :as models.user]
   [schema.core :as s]))

(s/defschema PeladaResponse
  {:id s/Int
   :organization_id s/Int
   (s/optional-key :organization_name) s/Str
   (s/optional-key :scheduled_at) s/Any
   (s/optional-key :num_teams) (s/maybe s/Int)
   (s/optional-key :players_per_team) (s/maybe s/Int)
   (s/optional-key :status) (s/maybe s/Str)
   (s/optional-key :closed_at) (s/maybe s/Any)})

(s/defschema PeladaBeginResponse
  {:matches_created s/Int})

(s/defschema TeamLineupSchema {(s/pred int? "int-key") [s/Any]})

(s/defschema PeladaDashboardResponse
  {:pelada s/Any
   :matches [s/Any]
   :teams [s/Any]
   :users [models.user/PublicUser]
   :organization_players [s/Any]
   :match_events [s/Any]
   :player_stats (s/maybe [s/Any])
   :team_players_map {s/Int [s/Any]}
   :match_lineups_map {s/Int TeamLineupSchema}})

(s/defschema PeladaFullDetailsResponse
  {s/Any s/Any}) ;; Keeping loose for now as it aggregates a lot
