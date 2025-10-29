(ns api-peladaapp.routes
  (:require
   [api-peladaapp.handlers.auth :as auth]
   [api-peladaapp.handlers.match :as handler.match]
   [api-peladaapp.handlers.organization :as handler.organization]
   [api-peladaapp.handlers.pelada :as handler.pelada]
   [api-peladaapp.handlers.player :as handler.player]
   [api-peladaapp.handlers.team :as handler.team]
   [api-peladaapp.handlers.substitution :as handler.substitution]
   [api-peladaapp.handlers.vote :as handler.vote]
   [api-peladaapp.handlers.user :as handler.user]
   [compojure.core     :refer [context defroutes DELETE GET POST PUT routes]]
   [compojure.route    :refer [not-found]]))

;; remove demo/test routes to silence kondo and reduce noise

(defroutes api-users
  (context "/api" []
    (GET "/users" [] handler.user/list-all)
    (GET "/user/:id" [] handler.user/get-by-id)
    (DELETE "/user/:id" [] handler.user/delete)
    (PUT "/user/:id" [] handler.user/update-by-id)))

(defroutes api-peladas
  (context "/api" []
    (POST "/peladas" [] handler.pelada/create)
    (GET "/peladas/:id" [] handler.pelada/get-by-id)
    (PUT "/peladas/:id" [] handler.pelada/update-by-id)
    (DELETE "/peladas/:id" [] handler.pelada/delete)
    (GET "/organizations/:organization_id/peladas" [] handler.pelada/list-by-org)
    (POST "/peladas/:id/begin" [] handler.pelada/begin)
    (POST "/peladas/:id/close" [] handler.pelada/close)))

(defroutes api-teams
  (context "/api" []
    (POST "/teams" [] handler.team/create)
    (GET "/teams/:id" [] handler.team/get-by-id)
    (GET "/teams/:id/players" [] handler.team/list-players)
    (PUT "/teams/:id" [] handler.team/update-by-id)
    (DELETE "/teams/:id" [] handler.team/delete)
    (GET "/peladas/:pelada_id/teams" [] handler.team/list-by-pelada)
    (POST "/teams/:id/players" [] handler.team/add-player)
    (DELETE "/teams/:id/players" [] handler.team/remove-player)))

(defroutes api-matches
  (context "/api" []
    (GET "/peladas/:pelada_id/matches" [] handler.match/list-by-pelada)
    (GET "/peladas/:pelada_id/events" [] handler.match/list-events-by-pelada)
    (GET "/peladas/:pelada_id/player-stats" [] handler.match/list-player-stats-by-pelada)
    (PUT "/matches/:id/score" [] handler.match/update-score)
    (POST "/matches/:id/events" [] handler.match/create-event)
    (DELETE "/matches/:id/events" [] handler.match/delete-event)
    ;; per-match lineups
    (GET "/matches/:id/lineups" [] handler.match/list-lineups)
    (POST "/matches/:id/lineups" [] handler.match/add-lineup-player)
    (DELETE "/matches/:id/lineups" [] handler.match/remove-lineup-player)
    (POST "/matches/:id/lineups/replace" [] handler.match/replace-lineup-player)))

(defroutes api-substitutions
  (context "/api" []
    (POST "/matches/:id/substitutions" [] handler.substitution/create)
    (GET "/matches/:id/substitutions" [] handler.substitution/list-by-match)))

(defroutes api-players
  (context "/api" []
    (POST "/players" [] handler.player/create)
    (GET "/players/:id" [] handler.player/get-by-id)
    (PUT "/players/:id" [] handler.player/update-by-id)
    (DELETE "/players/:id" [] handler.player/delete)
    (GET "/organizations/:organization_id/players" [] handler.player/list-by-org)))

(defroutes api-organizations
  (context "/api" []
    (POST "/organizations" [] handler.organization/create)
    (GET "/organizations" [] handler.organization/list-all)
    (GET "/organizations/:id" [] handler.organization/get-by-id)
    (PUT "/organizations/:id" [] handler.organization/update-by-id)
    (DELETE "/organizations/:id" [] handler.organization/delete)))

(defroutes api-votes
  (context "/api" []
    (POST "/votes" [] handler.vote/cast)
    (GET "/peladas/:pelada_id/votes" [] handler.vote/list-by-pelada)
    (GET "/peladas/:pelada_id/players/:player_id/normalized-score" [] handler.vote/normalize-score)))

(defroutes api-auth
  (context "/auth" []
    (POST "/login" [] auth/auth-handler)
    (POST "/register" [] handler.user/create)))

(defroutes gen-routes
  (not-found "404"))

(defn any-access [_]
  true)

(def access-rules [{:pattern #"^/auth/.*"
                    :handler any-access}
                   {:pattern #"^/api/.*"
                    :handler auth/authenticated-access}
                   {:pattern #"^/admin/.*"
                    :handler auth/admin-access}])

(def app-handler (routes api-auth
                         api-users
                         api-peladas
                         api-teams
                         api-matches
                         api-substitutions
                         api-organizations
                         api-players
                         api-votes
                         gen-routes))
