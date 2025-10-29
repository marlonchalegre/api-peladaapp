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
  [{:keys [email]} :- models.user/User
   secret :- s/Str]
  (-> {:email email
      :admin? false}
      (jwt/sign secret {:alg :hs512})))

