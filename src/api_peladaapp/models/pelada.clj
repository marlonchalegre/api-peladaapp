(ns api-peladaapp.models.pelada
  (:require
   [schema.core :as s]))

(s/defschema Pelada
  {:id (s/maybe s/Uuid)
   :organization-id s/Uuid
   :scheduled-at s/Str
   :status (s/maybe s/Str)
   :num-teams (s/maybe s/Int)
   :players-per-team (s/maybe s/Int)
   :fixed-goalkeepers (s/maybe s/Bool)
   :home-fixed-goalkeeper-id (s/maybe s/Uuid)
   :away-fixed-goalkeeper-id (s/maybe s/Uuid)
   :closed-at (s/maybe s/Any)
   (s/optional-key :timer-started-at) (s/maybe s/Any)
   (s/optional-key :timer-accumulated-ms) (s/maybe s/Int)
   (s/optional-key :timer-status) (s/maybe s/Str)})
