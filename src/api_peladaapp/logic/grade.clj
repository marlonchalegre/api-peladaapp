(ns api-peladaapp.logic.grade)

(defn performance-from-stars
  "Maps average stars (1-5) to a performance score (1-10) using a curved mapping:
   1.0 -> 1.0
   2.0 -> 4.0
   3.0 -> 7.0
   4.0 -> 9.0
   5.0 -> 10.0
   Linear interpolation is used between points."
  [stars]
  (let [x (double (or stars 3.0))]
    (cond
      (<= x 1.0) 1.0
      (<= x 2.0) (+ 1.0 (* 3.0 (- x 1.0)))
      (<= x 3.0) (+ 4.0 (* 3.0 (- x 2.0)))
      (<= x 4.0) (+ 7.0 (* 2.0 (- x 3.0)))
      (<= x 5.0) (+ 9.0 (* 1.0 (- x 4.0)))
      :else 10.0)))

(defn calculate-new-grade
  "Calculates the new grade based on the current grade and the performance in a pelada.
   Uses an Exponential Moving Average approach with a dampening factor K.
   K = 0.2 (Responsive: ~5 games to shift significantly)."
  ([old-grade performance]
   (calculate-new-grade old-grade performance 0.2))
  ([old-grade performance k]
   (let [current (double (or old-grade 5.0))
         perf (double performance)]
     (+ (* current (- 1.0 k))
        (* perf k)))))
