(ns api-peladaapp.logic.finance
  (:import
   [java.time LocalDate]))

(defn calculate-monthly-fine [year month payment-date fine-amount cut-off-day]
  (let [y (or year (.getYear (LocalDate/now)))
        m (or month (.getMonthValue (LocalDate/now)))
        d (or cut-off-day 5)
        deadline (LocalDate/of (int y) (int m) (int d))
        payment-ld (LocalDate/parse payment-date)]
    (if (.isAfter payment-ld deadline)
      (double (or fine-amount 0.0))
      0.0)))
