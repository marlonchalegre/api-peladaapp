(ns api-100folego.models.vote
  (:require [schema.core :as s]))

(s/defschema Vote
  {:id s/Int
   :pelada_id s/Int
   :voter_id s/Int
   :target_id s/Int
   :stars s/Int
   :created_at (s/maybe s/Any)})
