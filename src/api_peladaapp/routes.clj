(ns api-peladaapp.routes
  (:require
   [api-peladaapp.handlers.admin :as handler.admin]
   [api-peladaapp.handlers.attendance :as handler.attendance]
   [api-peladaapp.handlers.auth :as auth]
   [api-peladaapp.handlers.avatar :as handler.avatar]
   [api-peladaapp.handlers.finance :as handler.finance]
   [api-peladaapp.handlers.health :as handler.health]
   [api-peladaapp.handlers.internal :as handler.internal]
   [api-peladaapp.handlers.manual-stats :as handler.manual-stats]
   [api-peladaapp.handlers.match :as handler.match]
   [api-peladaapp.handlers.organization :as handler.organization]
   [api-peladaapp.handlers.pelada :as handler.pelada]
   [api-peladaapp.handlers.player :as handler.player]
   [api-peladaapp.handlers.randomize :as handlers.randomize]
   [api-peladaapp.handlers.super-admin :as handler.super-admin]
   [api-peladaapp.handlers.team :as handler.team]
   [api-peladaapp.handlers.user :as handler.user]
   [api-peladaapp.handlers.vote :as handler.vote]
   [clojure.string :as str]
   [compojure.core     :refer [context defroutes DELETE GET POST PUT routes]]
   [compojure.route    :refer [not-found]]))

(defroutes api-routes
  (context "/internal" []
    (POST "/scheduler/tick" [] handler.internal/trigger-scheduler)
    (GET "/waha/healthcheck" [] handler.health/waha-healthcheck)
    (POST "/waha/resume" [] handler.health/waha-resume)
    (POST "/waha/restart" [] handler.health/waha-restart)
    (POST "/profile/start" [] handler.internal/start-profiler)
    (POST "/profile/stop" [] handler.internal/stop-profiler)
    (POST "/profile/serve" [] handler.internal/serve-profiler-results))

  (context "/api" []
    (GET "/organizations/:organization_id/substitutions" [] handler.organization/list-substitutions)
    (POST "/organizations/:organization_id/substitutions" [] handler.organization/create-substitution)
    (POST "/organizations/:organization_id/substitutions/:sub_id/end" [] handler.organization/end-substitution)

    ;; Users
    (GET "/users" [] handler.user/list-all)
    (GET "/users/search" [] handler.user/search)
    (GET "/user/:id" [] handler.user/get-by-id)
    (PUT "/user/:id/profile" [] handler.user/update-profile)
    (DELETE "/user/:id" [] handler.user/delete)
    (GET "/users/me" [] auth/get-me-handler)
    (GET "/users/:user_id/organizations" [] handler.organization/list-by-user)

    ;; Avatars
    (POST "/user/:id/avatar" [] handler.avatar/upload)
    (GET "/user/:id/avatar" [] handler.avatar/serve)
    (DELETE "/user/:id/avatar" [] handler.avatar/delete)

    ;; Peladas
    (POST "/peladas" [] handler.pelada/create)
    (PUT "/peladas/:id" [] handler.pelada/update)
    (GET "/peladas/:id/schedule/preview" [] handler.pelada/get-schedule-preview)
    (POST "/peladas/:id/schedule" [] handler.pelada/save-schedule-plan)
    (GET "/peladas/:id/schedule" [] handler.pelada/get-schedule-plan)
    (GET "/peladas/:id/full-details" [] handler.pelada/get-full-details)
    (DELETE "/peladas/:id" [] handler.pelada/delete)
    (GET "/organizations/:organization_id/peladas" [] handler.pelada/list-by-org)
    (GET "/users/:user_id/peladas" [] handler.pelada/list-by-user)
    (POST "/peladas/:id/begin" [] handler.pelada/begin)
    (POST "/peladas/:id/close" [] handler.pelada/close)
    (POST "/peladas/:id/timer/start" [] handler.pelada/start-timer)
    (POST "/peladas/:id/timer/pause" [] handler.pelada/pause-timer)
    (POST "/peladas/:id/timer/reset" [] handler.pelada/reset-timer)
    (POST "/peladas/:id/attendance/batch" [] handler.attendance/batch-update-attendance)
    (POST "/peladas/:id/attendance" [] handler.attendance/update-attendance)
    (POST "/peladas/:id/attendance/voting-enabled" [] handler.attendance/update-voting-enabled)
    (POST "/peladas/:id/close-attendance" [] handler.attendance/close-attendance)

    (GET "/peladas/:id/dashboard-data" [] handler.pelada/get-dashboard-data)

    ;; Teams
    (POST "/teams" [] handler.team/create)
    (DELETE "/teams/:id" [] handler.team/delete)
    (POST "/teams/:id/players" [] handler.team/add-player)
    (DELETE "/teams/:id/players" [] handler.team/remove-player)

    ;; Matches
    (POST "/peladas/:id/teams/randomize" [] handlers.randomize/randomize-teams)
    (PUT "/matches/:id/score" [] handler.match/update-score)
    (POST "/matches/:id/timer/start" [] handler.match/start-timer)
    (POST "/matches/:id/timer/pause" [] handler.match/pause-timer)
    (POST "/matches/:id/timer/reset" [] handler.match/reset-timer)
    (POST "/matches/:id/events" [] handler.match/create-event)
    (DELETE "/matches/:id/events" [] handler.match/delete-event)
    (POST "/matches/:id/lineups" [] handler.match/add-lineup-player)
    (POST "/matches/:id/lineups/replace" [] handler.match/replace-lineup-player)

    ;; Players
    (POST "/players" [] handler.player/create)
    (PUT "/players/:id" [] handler.player/update-player-score)
    (DELETE "/players/:id" [] handler.player/delete)
    (GET "/organizations/:organization_id/players" [] handler.player/list-by-org)

    ;; Organizations
    (POST "/organizations" [] handler.organization/create)
    (GET "/organizations/:id" [] handler.organization/get-by-id)
    (PUT "/organizations/:id" [] handler.organization/update)
    (DELETE "/organizations/:id" [] handler.organization/delete)
    (POST "/organizations/:id/leave" [] handler.organization/leave)
    (GET "/organizations/:id/statistics" [] handler.organization/get-statistics)
    (GET "/organizations/:id/manual-stats" [] handler.manual-stats/list-manual-stats)
    (POST "/organizations/:id/manual-stats" [] handler.manual-stats/upsert-manual-stats)

    ;; Finance
    (GET "/organizations/:id/finance" [] handler.finance/get-finance)
    (PUT "/organizations/:id/finance" [] handler.finance/update-finance)
    (GET "/organizations/:id/finance/summary" [] handler.finance/get-summary)
    (GET "/organizations/:id/finance/transactions" [] handler.finance/list-transactions)
    (POST "/organizations/:id/finance/transactions" [] handler.finance/add-transaction)
    (POST "/organizations/:id/finance/transactions/:tx_id/reverse" [] handler.finance/reverse-transaction)
    (GET "/organizations/:id/finance/monthly-payments" [] handler.finance/get-monthly-payments)
    (POST "/organizations/:id/finance/monthly-payments" [] handler.finance/mark-monthly-payment)

    (POST "/organizations/:id/invite" [] handler.organization/invite)
    (POST "/organizations/:id/invite-link/reset" [] handler.organization/reset-invite-link)
    (POST "/organizations/:id/waha/test" [] handler.organization/test-waha)
    (GET "/organizations/:id/invite-link" [] handler.organization/get-invite-link)
    (GET "/organizations/:id/invitations" [] handler.organization/list-invitations)
    (DELETE "/organizations/:id/invitations/:invitation_id" [] handler.organization/revoke-invitation)
    (GET "/users/:user_id/organizations" [] handler.organization/list-by-user)
    (GET "/users/me" [] auth/get-me-handler)
    (GET "/invitations/pending" [] handler.organization/list-pending-invitations)
    (POST "/invitations/:token/accept" [_] handler.organization/accept-invitation)

    ;; Admins
    (POST "/organizations/:organization_id/admins" [] handler.admin/add-admin)
    (GET "/organizations/:organization_id/admins" [] handler.admin/list-by-organization)
    (DELETE "/organizations/:organization_id/admins/:user_id" [] handler.admin/remove-admin-by-org-and-user)

    ;; Votes
    (POST "/peladas/:id/votes/batch" [] handler.vote/batch-cast)
    (GET "/peladas/:id/voting-info" [] handler.vote/voting-info)
    (GET "/peladas/:id/voting-results" [] handler.vote/voting-results)
    (GET "/peladas/:id/voting-status" [] handler.vote/voting-status)

    ;; Health
    (GET "/health" [] handler.health/check)))

(defroutes auth-routes
  (context "/auth" []
    (POST "/login" [] auth/auth-handler)
    (POST "/logout" [] auth/logout-handler)
    (POST "/forgot-password" [] auth/forgot-password-handler)
    (POST "/reset-password" [] auth/reset-password-handler)
    (POST "/first-access" [] auth/first-access-handler)
    (POST "/register" [] handler.user/create)
    (GET "/invitations/:token" [_] handler.organization/get-invitation-info)))

(defroutes admin-routes
  (context "/api/admin" []
    (GET "/organizations" [] handler.super-admin/list-organizations)
    (POST "/organizations/:id/toggle-block" [] handler.super-admin/toggle-organization-block)
    (POST "/users/:id/toggle-block" [] handler.super-admin/toggle-user-block)
    (POST "/users/:id/toggle-org-creation" [] handler.super-admin/toggle-user-org-creation)
    (POST "/users/:id/toggle-super-admin" [] handler.super-admin/toggle-user-super-admin)))

(defroutes gen-routes
  (not-found {:status 404 :body {:error "Backend route not found"}}))

(defn any-access [_]
  true)

(defn internal-access [request]
  (let [remote-addr (:remote-addr request)]
    (or (= remote-addr "127.0.0.1")
        (= remote-addr "localhost")
        (= remote-addr "0:0:0:0:0:0:0:1")
        (str/starts-with? remote-addr "10.")
        (str/starts-with? remote-addr "172.")
        (str/starts-with? remote-addr "192.168."))))

(def access-rules [{:pattern #"^/internal/.*"
                    :handler internal-access}
                   {:pattern #"^/auth/.*"
                    :handler any-access}
                   {:pattern #"^/api/health"
                    :handler any-access}
                   {:pattern #"^/api/admin/.*"
                    :handler auth/admin-access}
                   {:pattern #"^/api/.*"
                    :handler auth/authenticated-access}])

(def app-handler (routes auth-routes
                         admin-routes
                         api-routes
                         gen-routes))
