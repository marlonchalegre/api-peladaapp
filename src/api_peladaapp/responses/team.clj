(ns api-peladaapp.responses.team
  (:require
   [schema.core :as s]))

(s/defschema TeamResponse
  {:id s/Uuid
   :pelada_id s/Uuid
   (s/optional-key :name) (s/maybe s/Str)})

(s/defschema TeamPlayerResponse
  {:team_id s/Uuid
   :player_id s/Uuid
   (s/optional-key :is_goalkeeper) s/Bool})
