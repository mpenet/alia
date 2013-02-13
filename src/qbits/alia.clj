(ns qbits.alia
  (:require
   [qbits.knit :as knit]
   [qbits.alia.codec :as codec])
  (:import
   [com.datastax.driver.core
    Cluster
    Cluster$Builder
    ColumnDefinitions$Definition
    DataType
    DataType$Name
    HostDistance
    PreparedStatement
    ProtocolOptions$Compression
    Query
    ResultSet
    ResultSetFuture
    Row
    Session
    SocketOptions]
   [com.google.common.util.concurrent
    Futures
    FutureCallback]))

(defmulti set-builder-option (fn [k ^Cluster$Builder builder option] k))

(defmethod set-builder-option :contact-points
  [_ builder hosts]
  (.addContactPoints ^Cluster$Builder builder
                     ^"[Ljava.lang.String;"
                     (into-array (if (sequential? hosts) hosts [hosts]))))

(defmethod set-builder-option :port
  [_ builder port]
  (.withPort ^Cluster$Builder builder (int port)))

(defmethod set-builder-option :load-balancing-policy
  [_ builder policy]
  builder)

(defmethod set-builder-option :pooling-options
  [_ builder options]
  builder)

(defmethod set-builder-option :metrics?
  [_ builder metrics?]
  (when (not metrics?)
    (.withoutMetrics ^Cluster$Builder builder)))

(defmethod set-builder-option :auth-info
  [_ builder options]
  builder)


(def compression {:none ProtocolOptions$Compression/NONE
                  :snappy ProtocolOptions$Compression/SNAPPY})

(defmethod set-builder-option :compression
  [_ builder option]
  (.withCompression ^Cluster$Builder builder (compression option)))

(defn set-builder-options
  ^Cluster$Builder
  [builder options]
  (reduce (fn [builder [k option]]
            (set-builder-option k builder option))
          builder
          options))

(defn cluster
  "Returns a new com.datastax.driver.core/Cluster instance"
  [hosts & {:as options
            :keys [pre-build-fn]
            :or {pre-build-fn identity}}]
  (-> (Cluster/builder)
      (set-builder-options (assoc options :contact-points hosts))
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

(def ^:dynamic *session*)

(defmacro with-session
  "Binds qbits.alia/*session*"
  [session & body]
  `(binding [qbits.alia/*session* ~session]
     ~@body))

(defn shutdown
  "Shutdowns Session or Cluster instance, clearing the underlying
pools/connections"
  ([cluster-or-session]
     (.shutdown cluster-or-session))
  ([]
     (.shutdown *session*)))

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
  [^Session session query executor success error]
  (let [rs-future (if (string? query)
                    (.executeAsync session ^String query)
                    (.executeAsync session ^Query query))
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
  [^Session session query]
  (result-set->clojure (if (string? query)
                         (.execute session ^String query)
                         (.execute session ^Query query))))

(defn execute
  "Executes querys against a session. Returns a collection of rows.
The first argument can be either a Session instance or the query
directly.

If you chose the latter the Session must be bound with
`with-session`.

If you pass :async? true, or if you provide a :success/:error callback
this will be asynchronous, returning a promise and triggering the
handler provided if any.  Also accepts a
custom :executor (java.util.concurrent.ExecutorService instance) to be
used for the asynchronous queries."
  [& args]
  (let [[^Session session query & {:keys [async? success error executor]
                                   :or {executor default-async-executor}}]
        (if (even? (count args))
          args
          (conj args *session*))]
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
