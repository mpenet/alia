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
(defmethod query-strategy :last-used
  [_ & [size]]
  (memo/memo-lu hayt/->raw size))

(def ^:dynamic *query* (query-strategy :last-used 100))

(def set-query-strategy!
  "Sets root value of *query-cach-fn*, allowing to change
   the cache factory, defaults to LU with a threshold of 100"
  (utils/var-root-setter *query*))
