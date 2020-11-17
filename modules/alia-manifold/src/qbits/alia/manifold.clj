(ns qbits.alia.manifold
  (:require
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [qbits.alia.codec :as codec]
   [qbits.alia.codec.default :as default-codec]
   [qbits.alia :refer [ex->ex-info query->statement set-statement-options!
                       get-executor]])
  (:import
   (com.datastax.driver.core
    Statement
    ResultSet
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
                                   result-set-fn row-generator codec
                                   retry-policy tracing? idempotent?
                                   fetch-size timestamp values paging-state
                                   read-timeout]}]
   (let [deferred (d/deferred)]
     (try
       (let [codec (or codec default-codec/codec)
             ^Statement statement (query->statement query values codec)]
         (set-statement-options! statement routing-key retry-policy
                                 tracing? idempotent?
                                 consistency serial-consistency fetch-size
                                 timestamp paging-state read-timeout)
         (let [^ResultSetFuture rs-future (.executeAsync session statement)]
           (d/on-realized deferred (or success (fn [_])) (or error (fn [_])))
           (Futures/addCallback
            rs-future
            (reify FutureCallback
              (onSuccess [_ result]
                (try
                  (d/success! deferred
                              (codec/result-set (.get rs-future)
                                                result-set-fn
                                                row-generator
                                                codec))
                  (catch Exception err
                    (d/error! deferred
                              (ex->ex-info err {:query statement :values values})))))
              (onFailure [_ ex]
                (d/error! deferred
                          (ex->ex-info ex {:query statement :values values}))))
            (get-executor executor))))
       (catch Exception e
         (d/error! deferred e)))
     deferred))
  ([^Session session query]
   (execute session query {})))


(defn execute-buffered
  "Allows to execute a query and have rows returned in a
  manifold stream. Every value in the stream is a single
  row. By default the query `:fetch-size` inherits from the cluster
  setting, unless you specify a different `:fetch-size` at query level
  and the stream inherits fetch size, unless you
  pass your own `:stream` with its own properties.
  If you close the stream the streaming process ends.
  Exceptions are sent to the stream as a value, it's your
  responsability to handle these how you deem appropriate. For options
  refer to `qbits.alia/execute` doc"
  ([^Session session query {:keys [executor consistency serial-consistency
                                   routing-key retry-policy
                                   result-set-fn row-generator codec
                                   tracing? idempotent?
                                   fetch-size buffer-size stream
                                   values timestamp
                                   paging-state read-timeout]}]
   (let [stream (or stream
                    (s/stream (or buffer-size
                                  ;; page-ahead buffering - fetches the next
                                  ;; page as soon as processing this page starts
                                  ;; which is a simple delay-avoiding heuristic
                                  (dec (or fetch-size
                                           (-> session
                                               .getCluster
                                               .getConfiguration
                                               .getQueryOptions
                                               .getFetchSize))))))]
     (try
       (let [codec (or codec default-codec/codec)
             ^Statement statement (query->statement query values codec)
             decode (:decoder codec)
             row-generator (or row-generator
                               codec/row-gen->map)

             stuff-available-records
             (fn [^ResultSet rs]
               (d/loop []
                 (d/chain
                  (d/success-deferred true)
                  (fn [_]
                    (if (> (.getAvailableWithoutFetching rs) 0)
                      (s/put! stream (codec/decode-row (.one rs)
                                                       row-generator
                                                       decode))
                      false))
                  (fn [ok?]
                    (if ok?
                      (d/recur)
                      rs)))))]

         (set-statement-options! statement routing-key retry-policy
                                 tracing? idempotent?
                                 consistency serial-consistency fetch-size
                                 timestamp paging-state read-timeout)
         (let [^ResultSetFuture rs-future (.executeAsync session statement)]
           (Futures/addCallback  rs-future
                                 (reify FutureCallback
                                   (onSuccess [_ rs]
                                     (d/catch

                                         (d/loop []

                                           (d/chain

                                            (d/success-deferred rs)

                                            stuff-available-records

                                            (fn [^ResultSet rs]
                                              (if (and (not (s/closed? stream))
                                                       (not (.isFullyFetched rs)))
                                                (let [p (d/deferred)]
                                                  (-> (.fetchMoreResults rs)
                                                      (Futures/addCallback
                                                       (reify FutureCallback
                                                         (onSuccess [_ r]
                                                           (d/success! p [::success r]))
                                                         (onFailure [_ ex] (d/success! p [::error ex])))
                                                       (get-executor executor)))
                                                  (d/chain
                                                   p
                                                   (fn [[k v]]
                                                     (if (= ::success k)
                                                       (d/recur)
                                                       (do
                                                         (s/put! stream (ex->ex-info
                                                                         v
                                                                         {:query statement
                                                                          :values values}))
                                                         (s/close! stream))))))
                                                (do
                                                  (s/close! stream))))))

                                         Exception
                                       (fn [ex]
                                         (s/put! stream (ex->ex-info ex
                                                                     {:query statement
                                                                      :values values}))
                                         (s/close! stream))))
                                   (onFailure [_ ex]
                                     (s/put! stream (ex->ex-info ex {:query statement :values values}))
                                     (s/close! stream)))
                                 (get-executor executor))))
       (catch Exception e
         (s/close! stream)
         (throw e)))
     (s/source-only stream)))
  ([^Session session query]
   (execute-buffered session query {})))
