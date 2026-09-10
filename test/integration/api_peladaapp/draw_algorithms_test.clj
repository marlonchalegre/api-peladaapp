(ns api-peladaapp.draw-algorithms-test
  "Integration tests for the Gemini and GPT team draws.

   These run the real SQL of logic.draw-history against Postgres, so they cover
   what the unit tests cannot: the history queries, the confirmed-attendance
   source, and the rows the draw writes into TeamPlayers."
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.draw :as logic.draw]
   [api-peladaapp.logic.draw-history :as draw.history]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(use-fixtures :each th/test-system-fixture)

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn- insert!
  [ds table row]
  (:id (jdbc/execute-one! ds
                          (hsql/format (-> (h/insert-into table)
                                           (h/values [row])
                                           (h/returning :id)))
                          opts)))

(defn- create-org! [ds name]
  (insert! ds :Organizations {:name name}))

(defn- create-player!
  "A user plus their OrganizationPlayers row. `grade` may be nil, which is how
   a player who has never been rated is stored."
  [ds org-id {:keys [name position grade]}]
  (let [user-id (insert! ds :Users {:name name
                                    :username (str "u-" (random-uuid))
                                    :email (str (random-uuid) "@test.com")
                                    :password "x"})]
    (insert! ds :OrganizationPlayers
             (cond-> {:user_id (misc/as-uuid user-id)
                      :organization_id (misc/as-uuid org-id)
                      :member_type [:cast "mensalista" :member_type]}
               position (assoc :position [:cast position :player_position])
               grade (assoc :grade grade)))))

(defn- create-pelada!
  [ds org-id {:keys [status scheduled-at players-per-team]
              :or {status "open" players-per-team 3}}]
  (insert! ds :Peladas
           (cond-> {:organization_id (misc/as-uuid org-id)
                    :status [:cast status :pelada_status]
                    :scheduled_at (if scheduled-at
                                    [[:cast scheduled-at :timestamp]]
                                    [:raw "CURRENT_TIMESTAMP"])}
             players-per-team (assoc :players_per_team players-per-team))))

(defn- create-teams!
  [ds pelada-id n]
  (mapv (fn [i] (insert! ds :Teams {:pelada_id (misc/as-uuid pelada-id)
                                    :name (str "Time " (inc i))}))
        (range n)))

(defn- confirm!
  [ds pelada-id player-ids]
  (doseq [pid player-ids]
    (insert! ds :Attendance {:pelada_id (misc/as-uuid pelada-id)
                             :player_id (misc/as-uuid pid)
                             :status [:cast "confirmed" :attendance_status]})))

(defn- team-players
  [ds pelada-id]
  (->> (jdbc/execute! ds
                      (hsql/format (-> (h/select [:t.id :team_id] [:tp.player_id :player_id])
                                       (h/from [:Teams :t])
                                       (h/left-join [:TeamPlayers :tp] [:= :tp.team_id :t.id])
                                       (h/where [:= :t.pelada_id (misc/as-uuid pelada-id)])))
                      opts)
       (filter :player_id)
       (group-by :team_id)))

(defn- squad
  "Twelve players in a 3-4-5 shape: enough to fill four teams of three."
  []
  (concat (map (fn [i] {:name (str "Zaga " i) :position "Defender" :grade (- 9.0 (* 0.3 i))}) (range 3))
          (map (fn [i] {:name (str "Meia " i) :position "Midfielder" :grade (- 8.8 (* 0.3 i))}) (range 4))
          (map (fn [i] {:name (str "Ata " i) :position "Striker" :grade (- 8.6 (* 0.2 i))}) (range 5))))

(deftest draw-uses-confirmed-attendance-test
  (let [ds (th/get-test-datasource)
        org-id (create-org! ds "Draw Org")
        pelada-id (create-pelada! ds org-id {:players-per-team 3})
        team-ids (create-teams! ds pelada-id 4)
        player-ids (mapv #(create-player! ds org-id %) (squad))]
    (confirm! ds pelada-id player-ids)

    (testing "the confirmed list is the source, not the ids the board sent"
      (let [result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                           {:algorithm "gemini"
                                            :use-history false
                                            ;; Deliberately empty: attendance wins.
                                            :player-ids []
                                            :players-per-team 3}
                                           ds)
            justification (:justification result)]
        (is (= "gemini" (:algorithm result)))
        (is (= "confirmed_attendance" (:source justification)))
        (is (= 12 (:players_considered justification)))))

    (testing "every confirmed player is written to a team exactly once"
      (let [by-team (team-players ds pelada-id)
            assigned (mapcat val by-team)]
        (is (= 4 (count by-team)))
        (is (= [3 3 3 3] (sort (map (comp count val) by-team))))
        (is (= (set (map misc/as-uuid player-ids))
               (set (map (comp misc/as-uuid :player_id) assigned))))
        (is (= (count assigned) (count (distinct (map :player_id assigned)))))))

    (testing "drawing again replaces the previous division instead of adding to it"
      (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                              {:algorithm "gpt" :use-history false
                               :player-ids [] :players-per-team 3}
                              ds)
      (let [assigned (mapcat val (team-players ds pelada-id))]
        (is (= 12 (count assigned)))))

    (testing "the teams reported line up with the teams on the board"
      (let [result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                           {:algorithm "gpt" :use-history false
                                            :player-ids [] :players-per-team 3}
                                           ds)
            by-team (team-players ds pelada-id)
            reported (:teams (:justification result))]
        (doseq [[idx team-id] (map-indexed vector team-ids)]
          (let [reported-ids (set (map (comp misc/as-uuid :id) (:players (nth reported idx))))
                stored-ids (set (map (comp misc/as-uuid :player_id)
                                     (get by-team (misc/as-uuid team-id))))]
            (is (= reported-ids stored-ids)
                (str "Team " (inc idx) " differs between the report and the board"))))))))

(deftest draw-falls-back-to-board-players-test
  (let [ds (th/get-test-datasource)
        org-id (create-org! ds "Fallback Org")
        pelada-id (create-pelada! ds org-id {:players-per-team 3})
        _ (create-teams! ds pelada-id 2)
        player-ids (mapv #(create-player! ds org-id %) (take 6 (squad)))]

    (testing "with no attendance recorded the ids from the board are used"
      (let [result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                           {:algorithm "gemini" :use-history false
                                            :player-ids (map misc/as-uuid player-ids)
                                            :players-per-team 3}
                                           ds)]
        (is (= "board" (get-in result [:justification :source])))
        (is (= 6 (get-in result [:justification :players_considered])))))))

(deftest draw-excludes-fixed-goalkeepers-test
  (let [ds (th/get-test-datasource)
        org-id (create-org! ds "GK Org")
        player-ids (mapv #(create-player! ds org-id %) (squad))
        [home-gk away-gk] player-ids
        pelada-id (create-pelada! ds org-id {:players-per-team 5})]
    (jdbc/execute! ds (hsql/format (-> (h/update :Peladas)
                                       (h/set {:fixed_goalkeepers true
                                               :home_fixed_goalkeeper_id (misc/as-uuid home-gk)
                                               :away_fixed_goalkeeper_id (misc/as-uuid away-gk)})
                                       (h/where [:= :id (misc/as-uuid pelada-id)]))))
    (create-teams! ds pelada-id 2)
    (confirm! ds pelada-id player-ids)

    (testing "the two fixed keepers stay out of the field division"
      (let [result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                           {:algorithm "gpt" :use-history false
                                            :player-ids [] :players-per-team 5}
                                           ds)
            drawn (set (map :id (mapcat :players (get-in result [:justification :teams]))))]
        (is (= 10 (get-in result [:justification :players_considered])))
        (is (not (contains? drawn (misc/as-uuid home-gk))))
        (is (not (contains? drawn (misc/as-uuid away-gk))))))))

(deftest draw-benches-players-over-capacity-test
  (let [ds (th/get-test-datasource)
        org-id (create-org! ds "Bench Org")
        pelada-id (create-pelada! ds org-id {:players-per-team 3})
        _ (create-teams! ds pelada-id 2)
        player-ids (mapv #(create-player! ds org-id %) (squad))]
    (confirm! ds pelada-id player-ids)

    (testing "only the capacity is drawn and the rest is reported, never dropped silently"
      (let [result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                           {:algorithm "gemini" :use-history false
                                            :player-ids [] :players-per-team 3}
                                           ds)
            justification (:justification result)]
        (is (= 6 (:players_considered justification)))
        (is (= 6 (count (:benched justification))))
        (is (= 12 (+ (:players_considered justification)
                     (count (:benched justification)))))
        (is (= 6 (count (mapcat val (team-players ds pelada-id)))))))))

(deftest draw-without-squad-size-test
  (testing "a pelada with no players_per_team spreads everyone instead of failing"
    (let [ds (th/get-test-datasource)
          org-id (create-org! ds "No Size Org")
          pelada-id (create-pelada! ds org-id {:players-per-team nil})
          _ (create-teams! ds pelada-id 4)
          player-ids (mapv #(create-player! ds org-id %) (squad))]
      (confirm! ds pelada-id player-ids)
      (let [result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                           {:algorithm "gpt" :use-history false
                                            :player-ids [] :players-per-team nil}
                                           ds)
            sizes (sort (map (comp count val) (team-players ds pelada-id)))]
        (is (= 12 (get-in result [:justification :players_considered])))
        (is (empty? (get-in result [:justification :benched])))
        (is (= [3 3 3 3] sizes))))))

(deftest draw-with-unrated-players-test
  (testing "a player with no grade gets a provisional one instead of a zero"
    (let [ds (th/get-test-datasource)
          org-id (create-org! ds "Unrated Org")
          pelada-id (create-pelada! ds org-id {:players-per-team 3})
          _ (create-teams! ds pelada-id 2)
          rated (mapv #(create-player! ds org-id %) (take 5 (squad)))
          unrated (create-player! ds org-id {:name "Sem Nota" :position "Striker" :grade nil})]
      (confirm! ds pelada-id (conj rated unrated))
      (let [result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                           {:algorithm "gpt" :use-history false
                                            :player-ids [] :players-per-team 3}
                                           ds)
            players (mapcat :players (get-in result [:justification :teams]))
            entry (first (filter #(= "Sem Nota" (:name %)) players))]
        (is (some? entry) "the unrated player should still be drawn")
        (is (true? (:provisional_grade entry)))
        ;; The squad sits around 8.5; a zero would have shown up here.
        (is (> (:grade entry) 5.0))
        (is (every? false? (map :provisional_grade
                                (filter #(not= "Sem Nota" (:name %)) players))))))))

(deftest draw-rejects-empty-setups-test
  (let [ds (th/get-test-datasource)
        org-id (create-org! ds "Empty Org")]

    (testing "a pelada with no teams is refused with a readable error"
      (let [pelada-id (create-pelada! ds org-id {:players-per-team 3})
            player-ids (mapv #(create-player! ds org-id %) (take 4 (squad)))]
        (confirm! ds pelada-id player-ids)
        (let [thrown (try
                       (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                               {:algorithm "gpt" :use-history false
                                                :player-ids [] :players-per-team 3}
                                               ds)
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown))
          (is (= :bad-request (:type (ex-data thrown))))
          (is (= "Pelada has no teams to fill" (:message (ex-data thrown)))))))

    (testing "a pelada with teams but nobody confirmed is refused too"
      (let [pelada-id (create-pelada! ds org-id {:players-per-team 3})]
        (create-teams! ds pelada-id 2)
        (let [thrown (try
                       (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                               {:algorithm "gemini" :use-history false
                                                :player-ids [] :players-per-team 3}
                                               ds)
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown))
          (is (= :bad-request (:type (ex-data thrown))))
          (is (= "No confirmed players to draw" (:message (ex-data thrown)))))))))

(deftest draw-classic-keeps-working-test
  (testing "the classic algorithm still runs and reports no justification"
    (let [ds (th/get-test-datasource)
          org-id (create-org! ds "Classic Org")
          pelada-id (create-pelada! ds org-id {:players-per-team 3})
          _ (create-teams! ds pelada-id 2)
          player-ids (mapv #(create-player! ds org-id %) (take 6 (squad)))
          result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                         {:algorithm "classic"
                                          :player-ids (map misc/as-uuid player-ids)
                                          :players-per-team 3}
                                         ds)]
      (is (= "classic" (:algorithm result)))
      (is (nil? (:justification result)))
      (is (= 6 (count (mapcat val (team-players ds pelada-id))))))))

(deftest draw-unknown-algorithm-falls-back-to-classic-test
  (testing "an unknown name is treated as the classic draw rather than failing"
    (let [ds (th/get-test-datasource)
          org-id (create-org! ds "Unknown Org")
          pelada-id (create-pelada! ds org-id {:players-per-team 3})
          _ (create-teams! ds pelada-id 2)
          player-ids (mapv #(create-player! ds org-id %) (take 6 (squad)))
          result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                         {:algorithm "definitely-not-an-algorithm"
                                          :player-ids (map misc/as-uuid player-ids)
                                          :players-per-team 3}
                                         ds)]
      (is (= "classic" (:algorithm result)))
      (is (nil? (:justification result))))))

;; ---------------------------------------------------------------------------
;; History signals
;; ---------------------------------------------------------------------------

(defn- play-night!
  "A finished pelada: two teams, one match with a result. Returns the pelada id."
  [ds org-id {:keys [home away home-score away-score scheduled-at]}]
  (let [pelada-id (create-pelada! ds org-id {:status "closed" :scheduled-at scheduled-at})
        [home-team away-team] (create-teams! ds pelada-id 2)]
    (doseq [[team-id roster] [[home-team home] [away-team away]]
            pid roster]
      (insert! ds :TeamPlayers {:team_id (misc/as-uuid team-id)
                                :player_id (misc/as-uuid pid)
                                :is_goalkeeper false}))
    (insert! ds :Matches {:pelada_id (misc/as-uuid pelada-id)
                          :home_team_id (misc/as-uuid home-team)
                          :away_team_id (misc/as-uuid away-team)
                          :sequence 1
                          :status [:cast "finished" :match_status]
                          :home_score home-score
                          :away_score away-score})
    pelada-id))

(deftest history-signals-test
  (let [ds (th/get-test-datasource)
        org-id (create-org! ds "History Org")
        players (mapv #(create-player! ds org-id %) (take 6 (squad)))
        [a b c d e f] players]
    ;; Two nights where A and B win together, so their duo carries two titles.
    (play-night! ds org-id {:home [a b c] :away [d e f]
                            :home-score 3 :away-score 1
                            :scheduled-at "2026-01-05 20:00:00"})
    (play-night! ds org-id {:home [a b d] :away [c e f]
                            :home-score 2 :away-score 0
                            :scheduled-at "2026-01-12 20:00:00"})

    (testing "closed nights before the cutoff are read"
      (let [history (draw.history/build (misc/as-uuid org-id) "2026-02-01 00:00:00.000" ds)]
        (is (= 2 (get-in history [:coverage :closed-nights-before-draw])))
        (is (= 2 (get-in history [:coverage :roster-nights-used])))
        (is (= 2 (get-in history [:coverage :title-nights-used])))))

    (testing "titles land on the winners only"
      (let [history (draw.history/build (misc/as-uuid org-id) "2026-02-01 00:00:00.000" ds)]
        (is (= 2 (get-in history [:titles (misc/as-uuid a)])))
        (is (= 2 (get-in history [:titles (misc/as-uuid b)])))
        (is (nil? (get-in history [:titles (misc/as-uuid f)])))))

    (testing "duos count nights together, nights available and titles together"
      (let [history (draw.history/build (misc/as-uuid org-id) "2026-02-01 00:00:00.000" ds)
            ab (draw.history/duo (misc/as-uuid a) (misc/as-uuid b))
            af (draw.history/duo (misc/as-uuid a) (misc/as-uuid f))]
        (is (= 2 (get-in history [:together ab])))
        (is (= 2 (get-in history [:available ab])))
        (is (= 2 (get-in history [:pair-titles ab])))
        (is (= 0 (get-in history [:together af] 0)))
        (is (= 2 (get-in history [:opposite af])))))

    (testing "the drought counts nights played since the last title"
      (let [history (draw.history/build (misc/as-uuid org-id) "2026-02-01 00:00:00.000" ds)]
        (is (= 0 (get-in history [:drought (misc/as-uuid a)])))
        (is (= 2 (get-in history [:drought (misc/as-uuid f)])))))

    (testing "a cutoff before the nights hides them entirely"
      (let [history (draw.history/build (misc/as-uuid org-id) "2026-01-01 00:00:00.000" ds)]
        (is (= 0 (get-in history [:coverage :closed-nights-before-draw])))
        (is (empty? (:titles history)))))

    (testing "the cutoff is exclusive, so a draw never reads its own night"
      (let [history (draw.history/build (misc/as-uuid org-id) "2026-01-12 20:00:00.000" ds)]
        (is (= 1 (get-in history [:coverage :closed-nights-before-draw])))
        (is (= 1 (get-in history [:titles (misc/as-uuid a)])))))))

(deftest history-splits-winning-duo-test
  (testing "with chemistry on, a duo that keeps winning together is separated"
    (let [ds (th/get-test-datasource)
          org-id (create-org! ds "Clique Org")
          players (mapv #(create-player! ds org-id %)
                        (map (fn [i] {:name (str "J" i) :position "Midfielder" :grade 8.0})
                             (range 8)))
          [a b] players]
      ;; Four nights won by the same pair puts them well past the clique threshold.
      (doseq [[idx day] (map-indexed vector ["2026-03-02" "2026-03-09" "2026-03-16" "2026-03-23"])]
        (play-night! ds org-id {:home [a b (nth players (+ 2 (mod idx 3)))]
                                :away [(nth players 5) (nth players 6) (nth players 7)]
                                :home-score 4 :away-score 0
                                :scheduled-at (str day " 20:00:00")}))
      (let [pelada-id (create-pelada! ds org-id {:players-per-team 4
                                                 :scheduled-at "2026-04-01 20:00:00"})]
        (create-teams! ds pelada-id 2)
        (confirm! ds pelada-id players)
        (let [result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                             {:algorithm "gemini" :use-history true
                                              :player-ids [] :players-per-team 4}
                                             ds)
              teams (get-in result [:justification :teams])
              team-of (fn [pid] (some (fn [team]
                                        (when (some #(= (misc/as-uuid pid) (misc/as-uuid (:id %)))
                                                    (:players team))
                                          (:index team)))
                                      teams))]
          (is (true? (get-in result [:justification :history :enabled])))
          (is (not= (team-of a) (team-of b))
              "the pair that keeps winning together should be split up"))))))

(deftest history-off-reports-nothing-test
  (testing "with history off the report says so and carries no chemistry terms"
    (let [ds (th/get-test-datasource)
          org-id (create-org! ds "No History Org")
          pelada-id (create-pelada! ds org-id {:players-per-team 3})
          _ (create-teams! ds pelada-id 2)
          player-ids (mapv #(create-player! ds org-id %) (take 6 (squad)))]
      (confirm! ds pelada-id player-ids)
      (doseq [algorithm ["gemini" "gpt"]]
        (let [result (logic.draw/draw-teams! (misc/as-uuid pelada-id)
                                             {:algorithm algorithm :use-history false
                                              :player-ids [] :players-per-team 3}
                                             ds)
              justification (:justification result)]
          (is (false? (get-in justification [:history :enabled])) algorithm)
          (is (false? (:use_history justification)) algorithm)
          (is (nil? (get-in justification [:metrics :champion_penalty])) algorithm))))))
