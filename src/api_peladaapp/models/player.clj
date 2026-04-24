(ns api-peladaapp.models.player
  (:require
   [schema.core :as s]))

(s/defschema Player
  {:id s/Int
   :user-id s/Int
   :organization-id s/Int
   :grade (s/maybe s/Num)
   :position-id (s/maybe s/Int)
   (s/optional-key :member-type) s/Str
   (s/optional-key :user-name) s/Str
   (s/optional-key :user-username) s/Str
   (s/optional-key :user-email) s/Str
   (s/optional-key :user-position) (s/maybe s/Str)
   (s/optional-key :user-avatar-filename) (s/maybe s/Str)})
