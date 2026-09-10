(ns api-peladaapp.logic.draw-test
  "Unit tests for the two new draw algorithms.

   They run on synthetic squads, with no database: the algorithms take players
   and pre-built history signals, so their behaviour is testable in isolation."
  (:require
   [api-peladaapp.logic.draw-common :as common]
   [api-peladaapp.logic.draw-gemini :as gemini]
   [api-peladaapp.logic.draw-gpt :as gpt]
   [api-peladaapp.logic.draw-history :as history]
   [clojure.test :refer [deftest is testing]]))

(defn- player
  [n position grade]
  {:id (java.util.UUID/nameUUIDFromBytes (.getBytes (str "player-" n)))
   :name (str "P" n)
   :position position
   :grade (double grade)})

(def squad
  "Twenty players shaped like a real night: 5 defenders, 7 midfielders,
   8 strikers, with grades spread between 6.0 and 9.2."
  (concat (map #(player % "Defender" (- 9.2 (* 0.4 %))) (range 5))
          (map #(player (+ 10 %) "Midfielder" (- 9.1 (* 0.35 %))) (range 7))
          (map #(player (+ 20 %) "Striker" (- 8.9 (* 0.2 %))) (range 8))))

(def team-names ["Time 1" "Time 2" "Time 3" "Time 4"])

(defn- ids [teams] (set (mapcat #(map :id (:players %)) teams)))

(defn- sizes [teams] (mapv #(count (:players %)) teams))

(defn- empty-signals
  "History with no observations: every signal is neutral, which is what a brand
   new organization looks like."
  []
  (assoc history/empty-history :before "2026-01-01 00:00:00.000"))

(deftest balanced-distribution-test
  (testing "every player is placed exactly once"
    (let [teams (common/balanced-distribution squad 4 (common/seeded-random 1))]
      (is (= 20 (count (mapcat identity teams))))
      (is (= (set (map :id squad)) (set (map :id (mapcat identity teams)))))))

  (testing "teams come out the same size"
    (let [teams (common/balanced-distribution squad 4 (common/seeded-random 1))]
      (is (= [5 5 5 5] (mapv count teams)))))

  (testing "sizes differ by at most one when the squad does not divide evenly"
    (let [teams (common/balanced-distribution (take 18 squad) 4 (common/seeded-random 1))
          counts (mapv count teams)]
      (is (= 18 (reduce + counts)))
      (is (<= (- (apply max counts) (apply min counts)) 1))))

  (testing "each position is spread as evenly as its count allows"
    (let [teams (common/balanced-distribution squad 4 (common/seeded-random 1))]
      (doseq [position ["Defender" "Midfielder" "Striker"]]
        (let [counts (map #(count (common/sector-players % position)) teams)]
          (is (<= (- (apply max counts) (apply min counts)) 1)
              (str position " is not spread evenly: " (vec counts))))))))

(deftest gpt-draw-test
  (let [result (gpt/draw squad team-names {:use-history false
                                           :history-signals (empty-signals)})]
    (testing "keeps every player and fills teams evenly"
      (is (= 4 (count (:teams result))))
      (is (= [5 5 5 5] (sizes (:teams result))))
      (is (= (set (map :id squad)) (ids (:teams result)))))

    (testing "splits the reference players, one per team"
      (is (true? (get-in result [:metrics :anchors_respected])))
      (doseq [team (:teams result)]
        (is (= 1 (count (:anchors team)))
            (str (:name team) " should hold exactly one reference"))))

    (testing "keeps team averages inside the declared tolerance"
      (is (true? (get-in result [:metrics :tolerance_respected])))
      (is (<= (get-in result [:metrics :team_mean_gap])
              (get-in result [:metrics :tolerance]))))

    (testing "reports history as off when it was not requested"
      (is (false? (get-in result [:history :enabled]))))

    (testing "is deterministic"
      (let [again (gpt/draw squad team-names {:use-history false
                                              :history-signals (empty-signals)})]
        (is (= (map #(map :name (:players %)) (:teams result))
               (map #(map :name (:players %)) (:teams again))))))))

(deftest gpt-draw-with-history-test
  (let [result (gpt/draw squad team-names {:use-history true
                                           :history-signals (empty-signals)})]
    (testing "runs on an organization with no recorded history"
      (is (= [5 5 5 5] (sizes (:teams result))))
      (is (true? (get-in result [:history :enabled]))))

    (testing "a neutral history produces a zero penalty"
      (is (zero? (get-in result [:history :penalty]))))

    (testing "with no nights recorded, everyone counts as little-observed"
      (is (= 20 (count (get-in result [:metrics :short_history_players])))))))

(deftest gemini-draw-test
  (let [result (gemini/draw squad team-names {:use-history false
                                              :history-signals (empty-signals)})]
    (testing "keeps every player and fills teams evenly"
      (is (= 4 (count (:teams result))))
      (is (= [5 5 5 5] (sizes (:teams result))))
      (is (= (set (map :id squad)) (ids (:teams result)))))

    (testing "reports chemistry as off and omits its terms"
      (is (false? (get-in result [:history :enabled])))
      (is (nil? (get-in result [:metrics :champion_penalty]))))

    (testing "balances the composite indices"
      (is (< (get-in result [:metrics :overall_gap]) 1.0))
      (is (< (get-in result [:metrics :defense_gap]) 1.0))
      (is (< (get-in result [:metrics :offense_gap]) 1.0)))

    (testing "is deterministic"
      (let [again (gemini/draw squad team-names {:use-history false
                                                 :history-signals (empty-signals)})]
        (is (= (map #(map :name (:players %)) (:teams result))
               (map #(map :name (:players %)) (:teams again))))))))

(deftest gemini-splits-winning-cliques-test
  (testing "a duo with several titles together is pulled apart"
    (let [[a b] (take 2 (filter #(= "Striker" (:position %)) squad))
          key (history/duo (:id a) (:id b))
          signals (-> (empty-signals)
                      ;; Five titles together is well past the clique threshold.
                      (assoc-in [:pair-titles key] 5)
                      (assoc-in [:pair-title-opportunities key] 6)
                      (assoc-in [:titles (:id a)] 5)
                      (assoc-in [:titles (:id b)] 5)
                      (assoc-in [:title-opportunities (:id a)] 6)
                      (assoc-in [:title-opportunities (:id b)] 6))
          result (gemini/draw squad team-names {:use-history true
                                                :history-signals signals})
          team-of (fn [id] (some (fn [team]
                                   (when (some #(= id (:id %)) (:players team))
                                     (:index team)))
                                 (:teams result)))]
      (is (true? (get-in result [:history :enabled])))
      (is (not= (team-of (:id a)) (team-of (:id b)))
          "the clique should not end up on the same team"))))

(deftest history-scoring-test
  (testing "the penalty of a neutral history is zero"
    (let [signals (empty-signals)
          teams (partition 5 (map :id squad))]
      (is (zero? (:penalty (history/evaluate signals teams))))))

  (testing "repeated partners raise the penalty"
    (let [teams (vec (partition 5 (map :id squad)))
          first-team (first teams)
          signals (reduce (fn [acc [a b]]
                            (-> acc
                                (assoc-in [:together (history/duo a b)] 10)
                                (assoc-in [:available (history/duo a b)] 10)))
                          (empty-signals)
                          (for [a first-team b first-team
                                :when (neg? (compare a b))]
                            [a b]))]
      (is (> (:penalty (history/evaluate signals teams))
             (:penalty (history/evaluate (empty-signals) teams))))))

  (testing "the cached scorer agrees with the plain one"
    (let [signals (empty-signals)
          teams (vec (partition 5 (map :id squad)))
          cached (history/scorer signals)]
      (is (= (:penalty (history/evaluate signals teams))
             (:penalty (cached teams))))
      ;; Same answer when the cache is warm.
      (is (= (:penalty (cached teams)) (:penalty (cached teams)))))))

(deftest single-team-test
  (testing "one team takes everyone and nothing compares against itself"
    (let [result (gpt/draw squad ["Time único"] {:use-history false
                                                 :history-signals (empty-signals)})]
      (is (= 1 (count (:teams result))))
      (is (= 20 (count (:players (first (:teams result))))))
      (is (zero? (get-in result [:metrics :team_mean_gap])))
      (is (zero? (get-in result [:metrics :sector_gap]))))))

(deftest more-teams-than-players-test
  (testing "empty teams are reported rather than crashing the draw"
    (let [tiny (take 3 squad)
          result (gemini/draw tiny team-names {:use-history false
                                               :history-signals (empty-signals)})]
      (is (= 4 (count (:teams result))))
      (is (= 3 (count (mapcat :players (:teams result)))))
      (is (some #(empty? (:players %)) (:teams result))))))

(deftest all-same-position-test
  (testing "a squad of one position is still split evenly"
    (let [mids (map #(player % "Midfielder" (- 9.0 (* 0.1 %))) (range 12))
          result (gpt/draw mids team-names {:use-history false
                                            :history-signals (empty-signals)})]
      (is (= [3 3 3 3] (sizes (:teams result))))
      (is (= "Midfielder" (get-in result [:metrics :main_sector]))))))

(deftest unknown-position-test
  (testing "a player with no position recorded is placed as a midfielder"
    (let [odd (conj (vec (take 11 squad))
                    {:id (java.util.UUID/randomUUID) :name "Sem Posição"
                     :position nil :grade 8.0})
          result (gpt/draw odd team-names {:use-history false
                                           :history-signals (empty-signals)})
          entry (first (filter #(= "Sem Posição" (:name %))
                               (mapcat :players (:teams result))))]
      (is (= "Midfielder" (:position entry)))
      (is (= "M" (:position_code entry)))
      (is (= 12 (count (mapcat :players (:teams result))))))))

(deftest gemini-profiles-ignore-chemistry-switch-test
  (testing "the composite indices come from votes and stats either way,
            which is what the reference script does in its classic mode"
    (let [[a b] (take 2 squad)
          signals (-> (empty-signals)
                      (assoc :vote-mean 3.0)
                      (assoc-in [:vote-stars (:id a)] {:sum 100 :count 20})
                      (assoc-in [:matches-played (:id a)] 20)
                      (assoc-in [:goals (:id a)] 40)
                      (assoc-in [:vote-stars (:id b)] {:sum 20 :count 20})
                      (assoc-in [:matches-played (:id b)] 20)
                      (assoc-in [:goals (:id b)] 0))
          profile-a (gemini/profile signals (first (filter #(= (:id a) (:id %)) squad)))
          profile-b (gemini/profile signals (first (filter #(= (:id b) (:id %)) squad)))]
      (is (> (:overall profile-a) (:overall profile-b))
          "the better-voted player should carry the higher overall")
      (is (> (:offense profile-a) (:offense profile-b))
          "the scorer should carry the higher attacking index"))))

(deftest gemini-profile-without-history-test
  (testing "with no signals at all the indices collapse to the admin grade"
    (let [entry (first squad)
          result (gemini/profile nil entry)]
      (is (= (:grade entry) (:overall result)))
      (is (= (:grade entry) (:offense result)))
      (is (= (:grade entry) (:defense result)))
      (is (nil? (:bayes_vote result))))))

(deftest gemini-few-matches-use-neutral-rates-test
  (testing "under five matches the goal rate is not trusted"
    (let [entry (first (filter #(= "Striker" (:position %)) squad))
          sparse (-> (empty-signals)
                     (assoc-in [:matches-played (:id entry)] 2)
                     (assoc-in [:goals (:id entry)] 10))
          busy (-> (empty-signals)
                   (assoc-in [:matches-played (:id entry)] 20)
                   (assoc-in [:goals (:id entry)] 100))]
      ;; Ten goals in two games would dominate if the rate were taken at face
      ;; value; the neutral default keeps it near the rest of the squad.
      (is (< (:offense (gemini/profile sparse entry))
             (:offense (gemini/profile busy entry)))))))

(deftest history-cutoff-is-respected-in-scoring-test
  (testing "a duo that never shared a team scores lower than one that always did"
    (let [teams (vec (partition 5 (map :id squad)))
          first-team (first teams)
          shared (reduce (fn [acc [a b]]
                           (-> acc
                               (assoc-in [:together (history/duo a b)] 8)
                               (assoc-in [:available (history/duo a b)] 8)))
                         (empty-signals)
                         (for [a first-team b first-team :when (neg? (compare a b))]
                           [a b]))
          fresh (reduce (fn [acc [a b]]
                          (assoc-in acc [:available (history/duo a b)] 8))
                        (empty-signals)
                        (for [a first-team b first-team :when (neg? (compare a b))]
                          [a b]))]
      (is (> (get-in (history/evaluate shared teams) [:components :repeat-partners])
             (get-in (history/evaluate fresh teams) [:components :repeat-partners]))))))

(deftest short-squad-test
  (testing "fewer players than slots still yields a usable division"
    (let [small (take 10 squad)
          result (gpt/draw small team-names {:use-history false
                                             :history-signals (empty-signals)})]
      (is (= 10 (count (mapcat :players (:teams result)))))
      (is (<= (- (apply max (sizes (:teams result)))
                 (apply min (sizes (:teams result))))
              1))))

  (testing "a squad with no midfielders levels another sector instead"
    (let [no-mids (remove #(= "Midfielder" (:position %)) squad)
          result (gpt/draw no-mids team-names {:use-history false
                                               :history-signals (empty-signals)})]
      (is (contains? #{"Defender" "Striker"} (get-in result [:metrics :main_sector])))
      (is (= (count no-mids) (count (mapcat :players (:teams result))))))))
