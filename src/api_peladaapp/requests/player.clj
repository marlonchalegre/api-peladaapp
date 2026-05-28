(ns api-peladaapp.requests.player
  (:require
   [schema.core :as s]))

(s/defschema CreatePlayerRequest
  {:user_id s/Uuid
   :organization_id s/Uuid
   (s/optional-key :grade) s/Num
   (s/optional-key :position) s/Str
   (s/optional-key :member_type) s/Str
   (s/optional-key :passing) s/Int
   (s/optional-key :ball_control) s/Int
   (s/optional-key :velocity) s/Int
   (s/optional-key :shooting) s/Int
   (s/optional-key :dribbling) s/Int
   (s/optional-key :defending) s/Int})

(s/defschema UpdatePlayerRequest
  {(s/optional-key :user_id) s/Uuid
   (s/optional-key :organization_id) s/Uuid
   (s/optional-key :grade) s/Num
   (s/optional-key :position) s/Str
   (s/optional-key :member_type) s/Str
   (s/optional-key :passing) s/Int
   (s/optional-key :ball_control) s/Int
   (s/optional-key :velocity) s/Int
   (s/optional-key :shooting) s/Int
   (s/optional-key :dribbling) s/Int
   (s/optional-key :defending) s/Int})
