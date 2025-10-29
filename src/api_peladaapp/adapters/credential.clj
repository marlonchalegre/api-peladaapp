(ns api-peladaapp.adapters.credential 
  (:require
    [schema.core :as s]))


(s/defn in->model
  [{:keys [email password]}]
  {:email email
   :password password})

(s/defn ->out
  "Convert token and user to output format"
  ([token]
   {:token token})
  ([token user]
   {:token token
    :user (select-keys user [:id :name :email])}))
