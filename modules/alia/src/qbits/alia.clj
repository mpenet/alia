(ns qbits.alia
  (:require
   [qbits.alia.codec :as codec]
   [qbits.alia.codec.default :as default-codec]
   [qbits.alia.udt :as udt]
   [qbits.alia.tuple :as tuple]
   [qbits.alia.enum :as enum]
;;   [qbits.alia.cluster-options :as copt]
   [qbits.alia.cql-session :as cql-session])
  (:import
   [com.datastax.oss.driver.api.core.session
    Session
    ]
   [com.datastax.oss.driver.api.core
    CqlSession]
   [com.datastax.oss.driver.api.core.cql
    Statement
    PreparedStatement
    BatchStatement
    BoundStatement
    BoundStatementBuilder
    SimpleStatement
    ]
   ;; (com.datastax.driver.core
   ;;  GuavaCompatibility
   ;;  LatencyTracker
   ;;  ResultSet
   ;;  ResultSetFuture
   ;; [com.google.common.util.concurrent
   ;;  Futures
   ;;  FutureCallback
   ;;  ListenableFuture]
   [java.util.concurrent Executor CompletionStage CompletableFuture]
   [java.nio ByteBuffer]
   [java.util Map]))

(defn ^:no-doc get-executor
  [x]
  (or x
      (reify
        Executor
        (execute [_ runnable]
          (.run runnable)))))

(defn ^:no-doc ex->ex-info
  ([^Exception ex data msg]
   (ex-info msg
            (merge {:type ::execute
                    :exception ex}
                   data)
            (.getCause ex)))
  ([ex data]
   (ex->ex-info ex data "Query execution failed")))

(defn bind
  "Takes a statement and a collection of values and returns a
  com.datastax.driver.core.BoundStatement instance to be used with
  `execute` (or one of its variants)

   Where values:
     Map: for named bindings       (i.e. INSERT INTO table (id, date) VALUES (:id :date))
     List: for positional bindings (i.e. INSERT INTO table (id, date) VALUES (?, ?))

  It also accepts an optional third argument, codec instance (see `execute`)"
  ([^PreparedStatement statement values]
   (bind statement values default-codec/codec))
  ([^PreparedStatement statement values {:keys [encoder]}]
   (try
     (if (map? values)
       (let [^BoundStatementBuilder builder (.boundStatementBuilder
                                             statement
                                             (to-array []))]
         (doseq [[k x] values]
           (codec/set-named-parameter! builder
                                       (name k)
                                       (encoder x)))
         (.build builder))
       (.bind statement (to-array (map encoder values))))
     (catch Exception ex
       (throw (ex->ex-info ex {:query statement
                               :type ::bind-error
                               :values values}
                           "Query binding failed"))))))

(defprotocol ^:no-doc PStatement
  (^:no-doc query->statement
    [q values codec] "Encodes input into a Statement instance"))

(extend-protocol PStatement
  Statement
  (query->statement [q _ _] q)

  PreparedStatement
  (query->statement [q values codec]
    (bind q values codec))

  String
  (query->statement [q values codec]
    (let [encode (:encoder codec)]
      (cond
        (nil? values)
        (SimpleStatement/newInstance q)

        (map? values)
        (SimpleStatement/newInstance
         q
         ^Map (reduce-kv (fn [m k v]
                           (assoc m (name k) (encode v)))
                         {}
                         values))

        :else
        (SimpleStatement/newInstance q (to-array (map encode values))))))

  BatchStatement
  (query->statement [bs values _]
    (when values
      (throw (ex-info {:type ::bind-error}
                      "You cannot bind values to batch statements directly,
               if you need to do so use qbits.alia/bind on your statements
               separately")))
    bs))

(defn prepare
  "Takes a session and a query (raw string or hayt) and returns a
  com.datastax.driver.core.PreparedStatement instance to be used in
  `execute` after it's been bound with `bind`. Hayt query parameter
  will be compiled with qbits.hayt/->raw internaly
  ex: (prepare session (select :foo (where {:bar ?})))"
  [^CqlSession session query]
  (let [^SimpleStatement q (query->statement query nil nil)]
    (try
      (.prepare session q)
      (catch Exception ex
        (throw (ex->ex-info ex
                            {:type ::prepare-error
                             :query q}
                            "Query prepare failed"))))))

(defn prepare-async
  "Takes a session, a query (raw string or hayt) and success and
   error callbacks and prepares a statement asynchronously. If
   successful the success callback will receive the
   com.datastax.driver.core.PreparedStatement instance, otherwise
   the error callback will receive an Exception"
  [^CqlSession session query {:keys [executor success error]}]
  (let [^SimpleStatement q (query->statement query nil nil)]
    (try
      (let [^CompletionStage prep-stage (.prepareAsync session q)]
        (.handleAsync
         prep-stage
         (fn [result ex]
           (if (some? ex)
             (when error
               (error (ex->ex-info ex {:type ::prepare-error
                                       :query q})))
             (when success
               (try
                 (success result)
                 (catch Exception err
                   (when error
                     (error (ex->ex-info err {:type ::prepare-error
                                              :query q}))))))))
         (get-executor executor)))
      (catch Exception ex
        (throw (ex->ex-info ex
                            {:type ::prepare-error
                             :query q}
                            "Query prepare failed"))))))

(defn batch
  "Takes a sequence of statements to be executed in batch.
  By default LOGGED, you can specify :logged :unlogged :counter as an
  optional second argument to control the type.  It also accepts an
  optional third argument, codec instance (see `execute`)"
  ([qs] (batch qs :logged))
  ([qs type]
   (batch qs type default-codec/codec))
  ([qs type codec]
   (let [bs (BatchStatement/newInstance (enum/batch-type type))]
     (doseq [q qs]
       (.add bs (query->statement q nil codec)))
     bs)))

(defmacro when-opt
  [opts k form]
  `(let [opt# (get ~opts ~(keyword k))]
     (when (some? opt#)
       (let [~(symbol k) opt#]
         ~form))))

(defn ^:no-doc set-statement-options!
  [^Statement statement
   {:keys [consistency execution-profile idempotent? page-size paging-state
           routing-key tracing?
           serial-consistency timestamp
           timeout]
    :as opts}]

  (when consistency
    (.setConsistencyLevel statement (enum/consistency-level consistency)))
  (when-opt opts execution-profile-name
    (.setExecutionProfileName statement execution-profile))
  (when-opt opts idempotent?
    (.setIdempotent statement idempotent?))
  (when-opt opts page-size
   (.setPageSize statement page-size))
  (when paging-state
    (.setPagingState statement paging-state))
  (when timestamp
    (.setQueryTimestamp statement timestamp))
  (when-opt opts routing-key
    (.setRoutingKey ^SimpleStatement statement
                    ^ByteBuffer routing-key))
  (when-opt opts routing-keyspace
    (.setRoutingKeyspace ^SimpleStatement statement
                         ^String routing-keyspace))
  (when-opt opts serial-consistency
    (.setSerialConsistencyLevel statement
                                (enum/consistency-level serial-consistency)))
  (when-opt opts timeout
    (.setTimeout statement timeout))
  (when-opt opts tracing?
    (.setTracing statement tracing?)))

(defn execute
  "Executes a query against a session.
  Returns a collection of rows.

  The query can be a raw string, a PreparedStatement (returned by
  `prepare`) with values passed via the `:values` option key will be bound by
  `execute`, BoundStatement (returned by `qbits.alia/bind`).

  The following options are supported:

* `:values` : values to be bound to a prepared query
* `:consistency` : Keyword, consistency
* `:serial-consistency` : Keyword, consistency
* `:routing-key` : ByteBuffer
* `:retry-policy` : one of qbits.alia.policy.retry/*
* `:tracing?` : Bool, toggles query tracing (available via query result metadata)
* `:fetch-size` : Number, sets query fetching size
* `:timestamp` : Number, sets the timestamp for query (if not specified in CQL)
* `:idempotent?` : Whether this statement is idempotent, i.e. whether
  it can be applied multiple times without changing the result beyond
  the initial application
* `:paging-state` : Expects a com.datastax.driver.core.PagingState
  instance. This will cause the next execution of this statement to
  fetch results from a given page, rather than restarting from the
  beginning
* `:result-set-fn` : Defaults to `clojure.core/seq` By default a
  result-set is an unchunked lazy seq, you can control this using this
  option. If you pass a function that supports IReduceInit you can
  have full control over how the resultset is formed (chunked,
  unchunked, eager or not, etc). A common use is to pass `#(into [] %)`
  as result-set-fn, you then get an eager value, with minimal copies,
  no intermediary seq and potentially better performance. This can be
  very powerfull when used right (for instance with transducers
  `#(into [] xform %))`.
* `:row-generator` : implements alia.codec/RowGenerator, Defaults to
  `alia.codec/row-gen->map` : A RowGenerator dicts how we construct rows.
* `:codec` : map of `:encoder` `:decoder` functions that control how to
  apply extra modifications on data sent/received (defaults to
  `qbits.alia.codec/default`).
* `:read-timeout` : Read timeout in milliseconds

  Possible values for consistency:

:all :any :each-quorum :local-one :local-quorum :local-serial :one :quorum
:serial :three :two"
  ([^CqlSession session query {:keys [consistency serial-consistency
                                      routing-key retry-policy
                                      result-set-fn row-generator
                                      tracing? idempotent? paging-state
                                      fetch-size values timestamp
                                      read-timeout codec]}]
   (let [codec (or codec default-codec/codec)
         ^Statement statement (query->statement query values codec)]
     (set-statement-options! statement routing-key retry-policy
                             tracing? idempotent?
                             consistency serial-consistency fetch-size
                             timestamp paging-state read-timeout)
     (try
       (codec/result-set (.execute session statement)
                         result-set-fn
                         row-generator
                         codec)
       (catch Exception err
         (throw (ex->ex-info err {:query statement :values values}))))))
  ;; to support old syle api with unrolled args
  ([^Session session query]
   (execute session query {})))

(defn handle-async-result-set-completion-stage
  [^CompletionStage completion-stage
   {next-page-handler :next-page-handler
    async-result-set-page-fn :async-result-set-page-fn
    row-generator :row-generator
    codec :codec
    statement :statement
    values :values}]
  (.handle
   completion-stage
   (fn [async-result-set ex]
     (if (some? ex)
       (throw
        (ex->ex-info ex {:query statement :values values}))

       (codec/async-result-set
        async-result-set
        async-result-set-page-fn
        next-page-handler
        row-generator
        codec)))))

(defn execute-async
  "Same execute but async and takes optional :success and :error
  callback functions via options. For options refer to
  `qbits.alia/execute` doc"
  ([^CqlSession session query {:keys [executor consistency serial-consistency
                                      routing-key retry-policy
                                      async-result-set-page-fn
                                      row-generator codec
                                      tracing? idempotent?
                                      fetch-size values timestamp
                                      paging-state read-timeout]
                               :as opts}]
   (try
     (let [codec (or codec default-codec/codec)
           ^Statement statement (query->statement query values codec)]
       (set-statement-options! statement routing-key retry-policy
                               tracing? idempotent?
                               consistency serial-consistency fetch-size
                               timestamp paging-state read-timeout)

       (let [handler (fn arscs-handler
                       [completion-stage]
                       (handle-async-result-set-completion-stage
                        completion-stage
                        (assoc opts
                               :statement statement
                               :next-page-handler arscs-handler)))
             ^CompletionStage async-result-set-cs (.executeAsync session statement)]
         (handler async-result-set-cs)))
     (catch Exception ex
       (CompletableFuture/completedFuture
        (ex->ex-info ex {:query query :values values})))))
  ([^CqlSession session query]
   (execute-async session query {})))

(defn ^:no-doc lazy-query-
  [session query pred coll opts]
  (lazy-cat coll
            (when query
              (let [coll (execute session query opts)]
                (lazy-query- session (pred query coll) pred coll opts)))))

(defn lazy-query
  "Takes a session, a query (raw or prepared) and a query modifier fn (that
  receives the last query and last chunk and returns a new query or nil).
  The first chunk will be the original query result, then for each
  subsequent chunk the query will be the result of last query
  modified by the modifier fn unless the fn returns nil,
  which would causes the iteration to stop.

  It also accepts any of `execute` options.

  ex: (lazy-query session
                (select :items (limit 2) (where {:x (int 1)}))
                        (fn [q coll]
                          (merge q (where {:si (-> coll last :x inc)})))
                {:consistency :quorum :tracing? true})"
  ([session query pred opts]
   (lazy-query- session query pred [] opts))
  ([session query pred]
   (lazy-query session query pred {})))

(defn udt-encoder
  ([session type]
   (udt/encoder session type default-codec/codec))
  ([session ks type]
   (udt/encoder session ks type default-codec/codec))
  ([session ks type codec]
   (udt/encoder session ks type codec)))

(defn tuple-encoder
  ([session table column]
   (tuple/encoder session table column default-codec/codec))
  ([session ks table column]
   (tuple/encoder session ks table column default-codec/codec))
  ([session ks table column codec]
   (tuple/encoder session ks table column codec)))
