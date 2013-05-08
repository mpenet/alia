(ns qbits.alia.hayt
  "Hayt Query strategies (query caching, raw query execution)"
  (:require [clojure.core.memoize :as memo]
            [qbits.hayt :as hayt]
            [qbits.alia.utils :as utils]))

(defmulti query-strategy (fn [k & _] k))

;; always compile
(defmethod query-strategy :raw
  [_ & _]
  hayt/->raw)

;; cache query in LU
(defmethod query-strategy :lu
  [_ & [size]]
  (memo/memo-lu hayt/->raw size))

(defmethod query-strategy :lru
  [_ & [size]]
  (memo/memo-lru hayt/->raw size))

(defmethod query-strategy :ttl
  [_ & [ms]]
  (memo/memo-ttl hayt/->raw ms))

(defmethod query-strategy :fifo
  [_ & [size]]
  (memo/memo-fifo hayt/->raw size))

(def ^:dynamic *query* (query-strategy :lu 100))

(def set-query-strategy!
  "Sets root value of *query-cach-fn*, allowing to change
   the cache factory, defaults to LU with a threshold of 100"
  (utils/var-root-setter *query*))
