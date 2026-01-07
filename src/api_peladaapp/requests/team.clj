(ns api-peladaapp.requests.team
  (:require
   [schema.core :as s]))

(s/defschema CreateTeamRequest
  {:pelada_id s/Int
   (s/optional-key :name) s/Str})

(s/defschema UpdateTeamRequest
  {(s/optional-key :pelada_id) s/Int
   (s/optional-key :name) s/Str})

(s/defschema AddPlayerToTeamRequest
  {:player_id s/Int})
