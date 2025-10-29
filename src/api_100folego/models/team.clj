(ns api-100folego.models.team
  (:require [schema.core :as s]))

(s/defschema Team
  {:id s/Int
   :pelada_id s/Int
   :name (s/maybe s/Str)})
