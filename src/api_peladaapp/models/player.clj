(ns api-peladaapp.models.player
  (:require [schema.core :as s]))

(s/defschema Player
  {:id s/Int
   :user_id s/Int
   :organization_id s/Int
   :grade (s/maybe s/Num)
   :position_id (s/maybe s/Int)})
