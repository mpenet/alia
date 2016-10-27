(ns qbits.alia.async
  (:require
   [qbits.alia :refer [ex->ex-info query->statement set-statement-options!
                       get-executor]]
   [qbits.alia.codec :as codec]
   [qbits.alia.codec.default :as default-codec]
   [clojure.core.async :as async])
  (:import
   (com.datastax.driver.core
    Statement
    ResultSetFuture
    Session
    Statement)
   (com.google.common.util.concurrent
    Futures
    FutureCallback)))

(defn execute-chan
  "Same as execute, but returns a clojure.core.async/promise-chan that is
  wired to the underlying ResultSetFuture. This means this is usable
  with `go` blocks or `take!`. Exceptions are sent to the channel as a
  value, it's your responsability to handle these how you deem
  appropriate.

  For options refer to `qbits.alia/execute` doc"
  ([^Session session query {:keys [executor consistency serial-consistency
                                   routing-key retry-policy result-set-fn codec
                                   tracing? idempotent?
                                   row-generator fetch-size values timestamp
                                   paging-state read-timeout]}]
   (let [ch (async/promise-chan)]
     (try
       (let [codec (or codec default-codec/codec)
             ^Statement statement (query->statement query values codec)]
         (set-statement-options! statement routing-key retry-policy tracing? idempotent?
                                 consistency serial-consistency fetch-size
                                 timestamp paging-state read-timeout)
         (let [^ResultSetFuture rs-future (.executeAsync session statement)]
           (Futures/addCallback
            rs-future
            (reify FutureCallback
              (onSuccess [_ result]
                (try
                  (async/put! ch (codec/result-set (.get rs-future)
                                                   result-set-fn
                                                   row-generator
                                                   codec))
                  (catch Exception err
                    (async/put! ch
                                (ex->ex-info err
                                             {:query statement
                                              :values values}))))
                (async/close! ch))
              (onFailure [_ ex]
                (async/put! ch
                            (ex->ex-info ex
                                         {:query statement
                                          :values values}))))
            (get-executor executor))))
       (catch Throwable t
         (async/put! ch t)))
     ch))
  ([^Session session query]
   (execute-chan session query {})))

(defn execute-chan-buffered
  "Allows to execute a query and have rows returned in a
  `clojure.core.async/chan`. Every value in the chan is a single
  row. By default the query `:fetch-size` inherits from the cluster
  setting, unless you specify a different `:fetch-size` at query level
  and the channel is a regular `clojure.core.async/chan`, unless you
  pass your own `:channel` with its own sizing
  caracteristics. `:fetch-size` dicts the chunking of the rows
  returned, allowing to stream rows into the channel in a controlled
  manner.
  If you close the channel the streaming process ends.
  Exceptions are sent to the channel as a value, it's your
  responsability to handle these how you deem appropriate. For options
  refer to `qbits.alia/execute` doc"
  ([^Session session query {:keys [executor consistency serial-consistency
                                   routing-key retry-policy
                                   result-set-fn row-generator codec
                                   tracing? idempotent?
                                   fetch-size values timestamp channel
                                   paging-state read-timeout]}]
   (let [ch (or channel (async/chan (or fetch-size (-> session
                                                       .getCluster
                                                       .getConfiguration
                                                       .getQueryOptions
                                                       .getFetchSize))))]
     (try
       (let [codec (or codec default-codec/codec)
             ^Statement statement (query->statement query values codec)]
         (set-statement-options! statement routing-key retry-policy
                                 tracing? idempotent?
                                 consistency serial-consistency fetch-size
                                 timestamp paging-state read-timeout)
         (let [^ResultSetFuture rs-future (.executeAsync session statement)]
           (Futures/addCallback
            rs-future
            (reify FutureCallback
              (onSuccess [_ result]
                (async/go
                  (try
                    (loop [rows (codec/result-set
                                 (.get ^ResultSetFuture rs-future)
                                 result-set-fn
                                 row-generator
                                 codec)]
                      (when-let [row (first rows)]
                        (when (async/>! ch row)
                          (recur (rest rows)))))
                    (catch Exception err
                      (async/put! ch
                                  (ex->ex-info err
                                               {:query statement
                                                :values values}))))
                  (async/close! ch)))
              (onFailure [_ ex]
                (async/put! ch (ex->ex-info ex
                                            {:query statement
                                             :values values}))
                (async/close! ch)))
            (get-executor executor))))
       (catch Throwable t
         (async/put! ch t)
         (async/close! ch)))
     ch))
  ([^Session session query]
   (execute-chan-buffered session query {})))
