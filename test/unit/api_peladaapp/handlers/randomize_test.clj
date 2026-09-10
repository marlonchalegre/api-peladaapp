(ns api-peladaapp.handlers.randomize-test
  (:require
   [api-peladaapp.controllers.pelada :as controller.pelada]
   [api-peladaapp.handlers.randomize :as handler.randomize]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.draw :as logic.draw]
   [clojure.test :refer [deftest is testing]]))

(def ^:private org-id (random-uuid))
(def ^:private pelada-id (random-uuid))
(def ^:private user-id (random-uuid))

(defn- request
  [body]
  {:params {:id (str pelada-id)}
   :body body
   :database "db"})

(defn- with-stubs
  "Runs `f` with authorization and the pelada lookup satisfied, capturing the
   options the handler hands to the draw."
  [draw-result f]
  (let [captured (atom nil)]
    (with-redefs [auth/get-user-id-from-request (fn [_] user-id)
                  auth/require-organization-admin! (fn [_ _ _] true)
                  controller.pelada/get-pelada (fn [_ _] {:organization-id org-id})
                  logic.draw/draw-teams! (fn [_ options _]
                                           (reset! captured options)
                                           (if (instance? Throwable draw-result)
                                             (throw draw-result)
                                             draw-result))]
      [(f) @captured])))

(deftest missing-pelada-id-test
  (testing "a request without a pelada id is a bad request"
    (let [resp (handler.randomize/randomize-teams {:params {} :body {} :database "db"})]
      (is (= 400 (:status resp))))))

(deftest defaults-to-classic-test
  (testing "the request is forwarded as-is; the draw owns the default"
    (let [[resp options] (with-stubs {:algorithm "classic" :justification nil}
                           #(handler.randomize/randomize-teams
                             (request {:player_ids [] :players_per_team 5})))]
      (is (= 200 (:status resp)))
      (is (nil? (:algorithm options)))
      (is (false? (:use-history options)))
      (is (= "classic" (get-in resp [:body :algorithm]))))))

(deftest passes-algorithm-and-history-test
  (testing "the selected algorithm and the chemistry flag reach the draw"
    (let [[_ options] (with-stubs {:algorithm "gemini" :justification {}}
                        #(handler.randomize/randomize-teams
                          (request {:player_ids [] :players_per_team 5
                                    :algorithm "gemini" :use_history true})))]
      (is (= "gemini" (:algorithm options)))
      (is (true? (:use-history options)))))

  (testing "players per team and the board ids are forwarded"
    (let [id (random-uuid)
          [_ options] (with-stubs {:algorithm "gpt" :justification {}}
                        #(handler.randomize/randomize-teams
                          (request {:player_ids [(str id)] :players_per_team 4
                                    :algorithm "gpt" :use_history false})))]
      (is (= 4 (:players-per-team options)))
      (is (= [id] (vec (:player-ids options)))))))

(deftest history-flag-is-strict-test
  (testing "only a real true enables chemistry — a truthy value does not"
    (doseq [value [nil "true" 1 "yes"]]
      (let [[_ options] (with-stubs {:algorithm "gemini" :justification {}}
                          #(handler.randomize/randomize-teams
                            (request {:player_ids [] :players_per_team 5
                                      :algorithm "gemini" :use_history value})))]
        (is (false? (:use-history options))
            (str "use_history " (pr-str value) " should not enable history"))))))

(deftest response-shape-test
  (testing "the justification is handed back alongside the algorithm"
    (let [justification {:algorithm "gpt" :teams [] :metrics {} :history {:enabled true}}
          [resp _] (with-stubs {:algorithm "gpt" :justification justification}
                     #(handler.randomize/randomize-teams
                       (request {:player_ids [] :players_per_team 5
                                 :algorithm "gpt" :use_history true})))]
      (is (= 200 (:status resp)))
      (is (true? (get-in resp [:body :success])))
      (is (= "gpt" (get-in resp [:body :algorithm])))
      (is (= justification (get-in resp [:body :justification]))))))

(deftest domain-errors-become-bad-requests-test
  (testing "a draw that cannot run reports 400, not an authentication failure"
    (let [error (ex-info "No confirmed players to draw"
                         {:type :bad-request :message "No confirmed players to draw"})
          [resp _] (with-stubs error
                     #(handler.randomize/randomize-teams
                       (request {:player_ids [] :players_per_team 5
                                 :algorithm "gemini" :use_history true})))]
      (is (= 400 (:status resp)))
      (is (= "No confirmed players to draw" (get-in resp [:body :message]))))))

(deftest non-admins-are-refused-test
  (testing "a member who is not an admin cannot draw the teams"
    (with-redefs [auth/get-user-id-from-request (fn [_] user-id)
                  controller.pelada/get-pelada (fn [_ _] {:organization-id org-id})
                  auth/require-organization-admin!
                  (fn [_ _ _]
                    (throw (ex-info "not admin" {:type :forbidden :message "not admin"})))
                  logic.draw/draw-teams! (fn [_ _ _]
                                           (throw (AssertionError. "must not run")))]
      (let [resp (handler.randomize/randomize-teams
                  (request {:player_ids [] :players_per_team 5 :algorithm "gemini"}))]
        (is (= 403 (:status resp)))))))

(deftest missing-pelada-becomes-404-test
  (testing "drawing a pelada that does not exist reports 404"
    (with-redefs [auth/get-user-id-from-request (fn [_] user-id)
                  controller.pelada/get-pelada
                  (fn [_ _] (throw (ex-info "Pelada not found"
                                            {:type :not-found :message "Pelada not found"})))]
      (let [resp (handler.randomize/randomize-teams
                  (request {:player_ids [] :players_per_team 5 :algorithm "gpt"}))]
        (is (= 404 (:status resp)))))))
