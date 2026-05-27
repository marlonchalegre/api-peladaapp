(ns api-peladaapp.models.player
  (:require
   [schema.core :as s]))

(s/defschema Player
  {:id s/Uuid
   :user-id s/Uuid
   :organization-id s/Uuid
   :grade (s/maybe s/Num)
   :position (s/maybe s/Str)
   (s/optional-key :member-type) s/Str
   (s/optional-key :user-name) s/Str
   (s/optional-key :user-username) s/Str
   (s/optional-key :user-position) (s/maybe s/Str)
   (s/optional-key :user-avatar-filename) (s/maybe s/Str)
   (s/optional-key :passing) (s/maybe s/Int)
   (s/optional-key :ball-control) (s/maybe s/Int)
   (s/optional-key :carrying) (s/maybe s/Int)
   (s/optional-key :shooting) (s/maybe s/Int)
   (s/optional-key :dribbling) (s/maybe s/Int)
   (s/optional-key :defending) (s/maybe s/Int)})
