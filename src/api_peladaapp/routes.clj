(ns api-peladaapp.routes
  (:require
   [api-peladaapp.handlers.admin :as handler.admin]
   [api-peladaapp.handlers.attendance :as handler.attendance]
   [api-peladaapp.handlers.auth :as auth]
   [api-peladaapp.handlers.health :as handler.health]
   [api-peladaapp.handlers.match :as handler.match]
   [api-peladaapp.handlers.organization :as handler.organization]
   [api-peladaapp.handlers.pelada :as handler.pelada]
   [api-peladaapp.handlers.player :as handler.player]
   [api-peladaapp.handlers.randomize :as handlers.randomize]
   [api-peladaapp.handlers.team :as handler.team]
   [api-peladaapp.handlers.user :as handler.user]
   [api-peladaapp.handlers.vote :as handler.vote]
   [compojure.core     :refer [context defroutes DELETE GET POST PUT routes]]
   [compojure.route    :refer [not-found]]))

;; remove demo/test routes to silence kondo and reduce noise

(defroutes api-users
  (context "/api" []
    (GET "/users" [] handler.user/list-all)
    (GET "/users/search" [] handler.user/search)
    (GET "/user/:id" [] handler.user/get-by-id)
    (PUT "/user/:id/profile" [] handler.user/update-profile)
    (DELETE "/user/:id" [] handler.user/delete)))

(defroutes api-peladas
  (context "/api" []
    (POST "/peladas" [] handler.pelada/create)
    (GET "/peladas/:id/full-details" [] handler.pelada/get-full-details)
    (DELETE "/peladas/:id" [] handler.pelada/delete)
    (GET "/organizations/:organization_id/peladas" [] handler.pelada/list-by-org)
    (GET "/users/:user_id/peladas" [] handler.pelada/list-by-user)
    (POST "/peladas/:id/begin" [] handler.pelada/begin)
    (POST "/peladas/:id/close" [] handler.pelada/close)
    (POST "/peladas/:id/attendance" [] handler.attendance/update-attendance)
    (POST "/peladas/:id/close-attendance" [] handler.attendance/close-attendance)
    (GET "/peladas/:id/dashboard-data" [] handler.pelada/get-dashboard-data)))

(defroutes api-teams
  (context "/api" []
    (POST "/teams" [] handler.team/create)
    (DELETE "/teams/:id" [] handler.team/delete)
    (POST "/teams/:id/players" [] handler.team/add-player)
    (DELETE "/teams/:id/players" [] handler.team/remove-player)))

(defroutes api-matches
  (context "/api" []
    (POST "/peladas/:id/teams/randomize" [] handlers.randomize/randomize-teams)
    (PUT "/matches/:id/score" [] handler.match/update-score)
    (POST "/matches/:id/events" [] handler.match/create-event)
    (DELETE "/matches/:id/events" [] handler.match/delete-event)
    ;; per-match lineups
    (POST "/matches/:id/lineups" [] handler.match/add-lineup-player)
    (POST "/matches/:id/lineups/replace" [] handler.match/replace-lineup-player)))

(defroutes api-players
  (context "/api" []
    (POST "/players" [] handler.player/create)
    (PUT "/players/:id" [] handler.player/update-player-score)
    (DELETE "/players/:id" [] handler.player/delete)
    (GET "/organizations/:organization_id/players" [] handler.player/list-by-org)))

(defroutes api-organizations
  (context "/api" []
    (POST "/organizations" [] handler.organization/create)
    (GET "/organizations/:id" [] handler.organization/get-by-id)
    (DELETE "/organizations/:id" [] handler.organization/delete)
    (POST "/organizations/:id/leave" [] handler.organization/leave)
    (GET "/organizations/:id/statistics" [] handler.organization/get-statistics)
    (POST "/organizations/:id/invite" [] handler.organization/invite)
    (GET "/organizations/:id/invite-link" [] handler.organization/get-invite-link)
    (GET "/organizations/:id/invitations" [] handler.organization/list-invitations)
    (DELETE "/organizations/:id/invitations/:invitation_id" [] handler.organization/revoke-invitation)
    (GET "/users/:user_id/organizations" [] handler.organization/list-by-user)
    (GET "/invitations/pending" [] handler.organization/list-pending-invitations)
    (POST "/invitations/:token/accept" [_] handler.organization/accept-invitation)))

(defroutes api-admins
  (context "/api" []
    (POST "/organizations/:organization_id/admins" [] handler.admin/add-admin)
    (GET "/organizations/:organization_id/admins" [] handler.admin/list-by-organization)
    (DELETE "/organizations/:organization_id/admins/:user_id" [] handler.admin/remove-admin-by-org-and-user)))

(defroutes api-votes
  (context "/api" []
    (POST "/peladas/:pelada_id/votes/batch" [] handler.vote/batch-cast)
    (GET "/peladas/:pelada_id/voting-info" [] handler.vote/voting-info)))

(defroutes api-auth
  (context "/auth" []
    (POST "/login" [] auth/auth-handler)
    (POST "/first-access" [] auth/first-access-handler)
    (POST "/register" [] handler.user/create)
    (GET "/invitations/:token" [_] handler.organization/get-invitation-info)))

(defroutes api-health
  (GET "/api/health" [] handler.health/check))

(defroutes gen-routes
  (not-found {:status 404 :body {:error "Backend route not found"}}))

(defn any-access [_]
  true)

(def access-rules [{:pattern #"^/auth/.*"
                    :handler any-access}
                   {:pattern #"^/api/health"
                    :handler any-access}
                   {:pattern #"^/api/.*"
                    :handler auth/authenticated-access}
                   {:pattern #"^/admin/.*"
                    :handler auth/admin-access}])

(def app-handler (routes api-auth
                         api-health
                         api-users
                         api-peladas
                         api-teams
                         api-matches
                         api-players
                         api-organizations
                         api-admins
                         api-votes
                         gen-routes))