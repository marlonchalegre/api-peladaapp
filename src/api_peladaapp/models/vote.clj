(ns api-peladaapp.models.vote
  (:require
   [schema.core :as s]))

(s/defschema Vote
  {:id s/Int
   :pelada-id s/Int
   :voter-id s/Int
   :target-id s/Int
   :stars s/Int
   :created-at (s/maybe s/Any)})
