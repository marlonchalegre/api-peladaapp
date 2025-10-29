(ns api-100folego.adapters.credential 
  (:require
    [schema.core :as s]))


(s/defn in->model
  [{:keys [email password]}]
  {:email email
   :password password})

(s/defn ->out
  [token]
  {:token token})
