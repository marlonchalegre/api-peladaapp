(ns api-peladaapp.requests.player
  (:require
   [schema.core :as s]))

(s/defschema CreatePlayerRequest
  {:user_id s/Uuid
   :organization_id s/Uuid
   (s/optional-key :grade) s/Num
   (s/optional-key :position) s/Str
   (s/optional-key :member_type) s/Str})

(s/defschema UpdatePlayerRequest
  {(s/optional-key :user_id) s/Uuid
   (s/optional-key :organization_id) s/Uuid
   (s/optional-key :grade) s/Num
   (s/optional-key :position) s/Str
   (s/optional-key :member_type) s/Str})
