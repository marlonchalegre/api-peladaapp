(ns api-peladaapp.requests.player
  (:require
   [schema.core :as s]))

(s/defschema CreatePlayerRequest
  {:user_id s/Int
   :organization_id s/Int
   (s/optional-key :grade) s/Num
   (s/optional-key :position_id) s/Int})

(s/defschema UpdatePlayerRequest
  {(s/optional-key :user_id) s/Int
   (s/optional-key :organization_id) s/Int
   (s/optional-key :grade) s/Num
   (s/optional-key :position_id) s/Int})
