(ns api-peladaapp.logic.user
  (:require
   [api-peladaapp.models.user :as models.user]
   [buddy.hashers :as hashers]
   [buddy.sign.jwt :as jwt]
   [schema.core :as s]))

(s/defn encrypt-password :- models.user/User
  [user :- models.user/User]
  (if (contains? user :password)
    (update user :password hashers/encrypt)
    user))

(s/defn build-token :- s/Str
  [{:keys [id email phone admin-orgs]} :- (assoc models.user/User :admin-orgs [s/Int])
   secret :- s/Str]
  (-> {:id id
       :email email
       :phone phone
       :admin? false
       :admin_orgs (or admin-orgs [])}
      (jwt/sign secret {:alg :hs512})))

