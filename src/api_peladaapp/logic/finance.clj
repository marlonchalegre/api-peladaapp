(ns api-peladaapp.logic.finance
  (:import
   [java.time LocalDate]))

(defn calculate-monthly-fine [year month payment-date fine-amount cut-off-day]
  (let [deadline (LocalDate/of (int year) (int month) (int cut-off-day))
        payment-ld (LocalDate/parse payment-date)]
    (if (.isAfter payment-ld deadline)
      (double (or fine-amount 0.0))
      0.0)))
