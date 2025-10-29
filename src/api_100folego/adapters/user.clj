(ns api-100folego.adapters.user
  (:require
   [api-100folego.helpers.misc :as misc]
   [api-100folego.models.user :as models.user]
   [medley.core :as medley.core]
   [schema.core :as s]))

;TODO define wires (which lib we should use?)
(defn in->model
  [{:keys [name email password]}]
  (medley.core/assoc-some {}
                          :name name
                          :email email
                          :password password))

(s/defn model->out
  [user :- models.user/User]
  (some-> user
          (select-keys [:id :name :email])))

(s/defn db->model :- models.user/User
  [user]
  (some-> user
          misc/unamespace
          (select-keys [:id :name :email :password])))
