(ns api-peladaapp.models.team
  (:require
   [schema.core :as s]))

(s/defschema Team
  {:id s/Uuid
   :pelada-id s/Uuid
   :name (s/maybe s/Str)})

(s/defschema TeamPlayer
  {:team-id s/Uuid
   :player-id s/Uuid
   (s/optional-key :is-goalkeeper) s/Bool})
