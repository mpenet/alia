(ns qbits.alia.hayt
  "Hot to handle hayt queries, "
  (:require [clojure.core.memoize :as memo]
            [qbits.hayt :as hayt]
            [qbits.alia.utils :as utils]))


(defmulti query-strategy (fn [k & _] k))

;; always compile
(defmethod query-strategy :default
  [q & _]
  (hayt/->raw q))

;; cache query in LU
(defmethod query-strategy :LU
  [_ & [size]]
  (memo/memo-lu hayt/->raw size))

(defmethod query-strategy :LRU
  [_ & [size]]
  (memo/memo-lru hayt/->raw size))

(defmethod query-strategy :TTL
  [_ & [ms]]
  (memo/memo-ttl hayt/->raw ms))

(defmethod query-strategy :FIFO
  [_ & [size]]
  (memo/memo-fifo hayt/->raw size))

(def ^:dynamic *query* (query-strategy :LU 100))

(def set-query-strategy!
  "Sets root value of *query-cach-fn*, allowing to change
   the cache factory, defaults to LU with a threshold of 100"
  (utils/var-root-setter *query*))
