(ns qbits.alia
  (:require
   [qbits.knit :as knit]
   [qbits.alia.codec :as codec]
   [qbits.alia.utils :as utils]
   [qbits.alia.cluster-options :as copt])
  (:import
   [com.datastax.driver.core
    Cluster
    Cluster$Builder
    ColumnDefinitions$Definition
    ConsistencyLevel
    DataType
    DataType$Name
    HostDistance
    PoolingOptions
    PreparedStatement
    ProtocolOptions$Compression
    Query
    ResultSet
    ResultSetFuture
    Row
    Session
    SimpleStatement
    SocketOptions]
   [com.google.common.util.concurrent
    Futures
    FutureCallback]
   [java.nio ByteBuffer]))

(def ^:dynamic *consistency* :one)
(def consistency-levels (utils/enum-values->map (ConsistencyLevel/values)))

(defmacro with-consistency
  "Binds qbits.alia/*consistency*"
  [consistency & body]
  `(binding [qbits.alia/*consistency* ~consistency]
     ~@body))

(defn set-consistency!
  "Sets the consistency globally"
  [consistency]
  (alter-var-root #'*consistency*
                  (constantly consistency)
                  (when (thread-bound? #'*consistency*)
                    (set! *consistency* consistency))))

(def ^:dynamic *session*)

(defmacro with-session
  "Binds qbits.alia/*session*"
  [session & body]
  `(binding [qbits.alia/*session* ~session]
     ~@body))

(defn set-session!
  "Sets the session globally"
  [session]
  (alter-var-root #'*session*
                  (constantly session)
                  (when (thread-bound? #'*session*)
                    (set! *session* session))))

(defn cluster
  "Returns a new com.datastax.driver.core/Cluster instance"
  [hosts & {:as options
            :keys [pre-build-fn]
            :or {pre-build-fn identity}}]
  (-> (Cluster/builder)
      (copt/set-cluster-options! (assoc options :contact-points hosts))
      ^Cluster$Builder (pre-build-fn)
      .build))

(defn ^Session connect
  "Returns a new com.datastax.driver.core/Session instance. We need to
have this separate in order to allow users to connect to multiple
keyspaces from a single cluster instance"
  ([^Cluster cluster keyspace]
     (.connect cluster keyspace))
  ([^Cluster cluster]
     (.connect cluster)))

(defn shutdown
  "Shutdowns Session or Cluster instance, clearing the underlying
pools/connections"
  ([^Session cluster-or-session]
     (.shutdown cluster-or-session))
  ([]
     (shutdown *session*)))

(defn prepare
  ([session query]
     (.prepare ^Session session query))
  ([query]
     (prepare *session* query)))

(defn result-set->clojure
  [result-set]
  (map (fn [^Row row]
         (let [cdef (.getColumnDefinitions row)]
           (map-indexed
            (fn [idx col]
              (let [idx (int idx)]
                {:name (.getName cdef idx)
                 :value (codec/decode row idx (.getType cdef idx))}))
            cdef)))
       result-set))

(defonce default-async-executor (knit/executor :cached))

(defn execute-async
  [^Session session ^SimpleStatement statement executor success error]
  (let [rs-future
        (.executeAsync session (SimpleStatement. statement))
        async-result (promise)]
    (Futures/addCallback
     rs-future
     (reify FutureCallback
       (onSuccess [_ result]
         (let [result (result-set->clojure (.get ^ResultSetFuture rs-future))]
           (deliver async-result result)
           (when (fn? success)
             (success result))))
       (onFailure [_ err]
         (deliver async-result err)
         (when (fn? error)
           (error err))))
     executor)
    async-result))

(defn execute-sync
  [^Session session ^SimpleStatement statement]
  (result-set->clojure (.execute session statement)))

(defn execute
  "Executes querys against a session. Returns a collection of rows.
The first argument can be either a Session instance or the query
directly.

So 2 signatures:

 [session query & {:keys [async? success error executor
                          consistency routing-key retry-policy
                          tracing?]
                  :or {executor default-async-executor
                       consistency *consistency*}}]

or

 [query & {:keys [async? success error executor
                  consistency routing-key retry-policy
                  tracing?]
                  :or {executor default-async-executor
                       consistency *consistency*}}]

If you chose the latter the Session must be bound with
`with-session`.

If you pass :async? true, or if you provide a :success/:error callback
this will be asynchronous, returning a promise and triggering the
handler provided if any.  Also accepts a
custom :executor (java.util.concurrent.ExecutorService instance) to be
used for the asynchronous queries."
  [& args]
  (let [[^Session session query & {:keys [async? success error executor
                                          consistency routing-key retry-policy
                                          tracing?]
                                   :or {executor default-async-executor
                                        consistency *consistency*}}]
        (if (even? (count args))
          args
          (conj args *session*))
        statement (SimpleStatement. query)]
    (when retry-policy
      (.setRetryPolicy statement retry-policy))
    (when routing-key
      (.setRoutingKey statement ^ByteBuffer routing-key))
    (when tracing?
      (.enableTracing statement))

    (.setConsistencyLevel statement (consistency-levels consistency))

    (if (or success async? error)
      (execute-async session query executor success error)
      (execute-sync session query))))

(defn prepare
  "Returns a com.datastax.driver.core.PreparedStatement instance to be
used in `execute` after it's been bound with `bind`"
  ([^Session session ^String query]
     (.prepare session query))
  ([^String query]
     (prepare *session* query)))

(defn bind
  "Returns a com.datastax.driver.core.BoundStatement instance to be
  used with `execute`"
  [^PreparedStatement prepared-statement & values]
  (.bind prepared-statement (to-array (map codec/encode values))))

(defn rows->maps
  "Converts rows returned from execute into a collection of maps,
  instead of 2d collection with maps per entry"
  [rows]
  (map #(into (array-map) (map (juxt :name :value) %)) rows))
