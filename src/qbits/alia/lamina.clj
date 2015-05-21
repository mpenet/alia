(ns qbits.alia.lamina
  (:require
   [lamina.core :as l]
   [qbits.alia.codec :as codec]
   [qbits.alia :refer [ex->ex-info query->statement set-statement-options!
                       get-executor]])
  (:import
   (com.datastax.driver.core
    Statement
    ResultSetFuture
    Session
    Statement)
   (com.google.common.util.concurrent
    Futures
    FutureCallback)))

(defn execute
  "Same as execute, but returns a promise and accepts :success
  and :error handlers via options, you can also pass :executor via the
  option map for the ResultFuture, it defaults to a cachedThreadPool
  if you don't.

  For other options refer to `qbits.alia/execute` doc"
  ([^Session session query {:keys [success error executor consistency
                                   serial-consistency routing-key
                                   retry-policy tracing? string-keys? idempotent?
                                   fetch-size values timestamp paging-state]}]
     (let [^Statement statement (query->statement query values)]
       (set-statement-options! statement routing-key retry-policy
                               tracing? idempotent?
                               consistency serial-consistency fetch-size
                               timestamp paging-state)
       (let [^ResultSetFuture rs-future
             (try
               (.executeAsync session statement)
               (catch Exception ex
                 (throw (ex->ex-info ex {:query statement :values values}))))
             async-result (l/result-channel)]
         (l/on-realized async-result success error)
         (Futures/addCallback
          rs-future
          (reify FutureCallback
            (onSuccess [_ result]
              (l/success async-result
                         (codec/result-set->maps (.get rs-future) string-keys?)))
            (onFailure [_ ex]
              (l/error async-result
                       (ex->ex-info ex {:query statement :values values}))))
          (get-executor executor))
         async-result)))
    ([^Session session query]
       (execute session query {})))

;; just for backward compat's sake
(def execute-async execute)
