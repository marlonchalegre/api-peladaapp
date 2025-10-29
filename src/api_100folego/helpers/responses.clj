(ns api-100folego.helpers.responses )


;; Semantic response helpers - always return JSON bodies
(defn ok [d] {:status 200 :body (or d {})})
(defn bad-request [d] {:status 400 :body (or d {:error "bad-request"})})
(defn unauthorized [d] {:status 401 :body (or d {:error "unauthorized"})})
(defn forbidden [d] {:status 403 :body (or d {:error "forbidden"})})
(defn server-error [d] {:status 500 :body (or d {:error "server-error"})})
(defn not-found [d] {:status 404 :body (or d {:error "not-found"})})
(defn deleted [_] {:status 200 :body {}})
(defn updated [d]
  {:status 200
   :body (cond
           (nil? d) {}
           (map? d) d
           :else {:result d})})

(defn created [d]
  {:status 201
   :body (cond
           (nil? d) {}
           (map? d) d
           :else {:result d})})
