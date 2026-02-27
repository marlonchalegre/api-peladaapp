(ns api-peladaapp.responses.team
  (:require
   [schema.core :as s]))

(s/defschema TeamResponse
  {:id s/Int
   :pelada_id s/Int
   (s/optional-key :name) (s/maybe s/Str)})

(s/defschema TeamPlayerResponse
  {:team_id s/Int
   :player_id s/Int
   (s/optional-key :is_goalkeeper) s/Bool})
