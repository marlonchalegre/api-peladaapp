(ns api-peladaapp.responses.player
  (:require
   [schema.core :as s]))

(s/defschema PlayerResponse
  {:id s/Uuid
   :user_id s/Uuid
   :organization_id s/Uuid
   (s/optional-key :grade) (s/maybe s/Num)
   (s/optional-key :position) (s/maybe s/Str)
   (s/optional-key :member_type) s/Str
   (s/optional-key :user_name) s/Str
   (s/optional-key :user_username) s/Str
   (s/optional-key :user_position) (s/maybe s/Str)
   (s/optional-key :user_avatar_filename) (s/maybe s/Str)
   (s/optional-key :attendance_status) (s/maybe s/Str)
   (s/optional-key :attendance_updated_at) (s/maybe s/Any)
   (s/optional-key :passing) (s/maybe s/Int)
   (s/optional-key :ball_control) (s/maybe s/Int)
   (s/optional-key :carrying) (s/maybe s/Int)
   (s/optional-key :shooting) (s/maybe s/Int)
   (s/optional-key :dribbling) (s/maybe s/Int)
   (s/optional-key :defending) (s/maybe s/Int)})
