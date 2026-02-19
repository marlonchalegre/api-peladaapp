(ns api-peladaapp.responses.player
  (:require
   [schema.core :as s]))

(s/defschema PlayerResponse
  {:id s/Int
   :user_id s/Int
   :organization_id s/Int
   (s/optional-key :grade) (s/maybe s/Num)
   (s/optional-key :position_id) (s/maybe s/Int)
   (s/optional-key :user_name) s/Str
   (s/optional-key :user_email) s/Str})
