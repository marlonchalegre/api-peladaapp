(ns api-peladaapp.models.player
  (:require
   [schema.core :as s]))

(s/defschema Player
  {:id s/Int
   :user-id s/Int
   :organization-id s/Int
   :grade (s/maybe s/Num)
   :position-id (s/maybe s/Int)})
