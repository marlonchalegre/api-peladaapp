(ns api-peladaapp.models.vote
  (:require
   [schema.core :as s]))

(s/defschema Vote
  {:id s/Uuid
   :pelada-id s/Uuid
   :voter-id s/Uuid
   :target-id s/Uuid
   :stars s/Int
   :created-at (s/maybe s/Any)})
