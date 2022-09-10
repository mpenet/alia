(ns qbits.alia
  (:require
   [qbits.alia.codec.default :as default-codec]
   [qbits.alia.completable-future :as cf]
   [qbits.alia.cql-session :as cql-session]
   [qbits.alia.error :as err]
   [qbits.alia.result-set :as result-set]
   [qbits.alia.settable-by-name :as settable-by-name]
   [qbits.alia.udt :as udt]
   [qbits.alia.tuple :as tuple]
   [qbits.alia.enum :as enum])
  (:import
   [com.datastax.oss.driver.api.core CqlSessionBuilder CqlSession]
   [com.datastax.oss.driver.api.core.cql
    Statement
    PreparedStatement
    BatchStatement
    BoundStatementBuilder
    SimpleStatement]
   [java.util.concurrent Executor CompletionStage ExecutionException]
   [java.nio ByteBuffer]
   [java.util Map]))

(defn session
  "shortcut to build a `CqlSession` from a config map

   config map keys are keywords from the
   `qbits.alia.enum` of
   `com.datastax.oss.driver.api.core.config.DefaultDriverOption`
   and appropriate scalar or collection values e.g.

   ```
   {:session-keyspace \"alia\"
    :contact-points [\"localhost:9042\"]
    :load-balancing-local-datacenter \"Analytics\"}
   ```"
  ([] (session {}))
  ([config]
   (let [^CqlSessionBuilder sb (cql-session/cql-session-builder config)]
     (.build sb))))

(defn session-async
  "like `session`, but returns a CompletionStage<CqlSession>"
  ([] (session-async {}))
  ([config]
   (let [^CqlSessionBuilder sb (cql-session/cql-session-builder config)]
     (.buildAsync sb))))

(defn close
  "close a `CqlSession`"
  [^CqlSession session]
  (.close session))

(defn close-async
  "close `CqlSession` async"
  [^CqlSession session]
  (.closeAsync session))

(defn ^:no-doc get-executor
  [x]
  (or x
      (reify
        Executor
        (execute [_ runnable]
          (.run runnable)))))

(defn bind
  "Takes a statement and a collection of values and returns a
  `com.datastax.oss.driver.api.core.cql.BoundStatement` instance to be used with
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
           (settable-by-name/set-named-parameter!
            builder
            (if (keyword? k) (str (.-sym k)) k)
            (encoder x)))
         (.build builder))
       (.bind statement (to-array (map encoder values))))
     (catch Exception ex
       (throw (err/ex->ex-info ex {:statement statement
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
      (throw
       (ex-info {:type ::bind-error}
                "You cannot bind values to batch statements directly,
                 if you need to do so use qbits.alia/bind on your statements
                 separately")))
    bs))

(defn prepare
  "Takes a `CqlSession` and a query (raw string or hayt) and returns a
  `com.datastax.oss.driver.api.core.cql.PreparedStatement` instance to be used
  in `execute` after it's been bound with `bind`. Hayt query parameter
  will be compiled with qbits.hayt/->raw internaly
  ex: (prepare session (select :foo (where {:bar ?})))"
  [^CqlSession session query]
  (let [^SimpleStatement q (query->statement query nil nil)]
    (try
      (.prepare session q)
      (catch Exception ex
        (throw (err/ex->ex-info ex
                                {:type ::prepare-error
                                 :statement q}
                                "Query prepare failed"))))))

(defn prepare-async
  "Takes a `CqlSession`, a query (raw string or hayt) and
   prepares a statement asynchronously.

   returns CompletionState<PreparedStatement>"
  [^CqlSession session query {:keys [executor] :as opts}]
  (let [^SimpleStatement q (query->statement query nil nil)]
    (cf/handle-completion-stage
     (.prepareAsync session q)
     identity
     (fn [ex]
       (throw
        (err/ex->ex-info ex
                         {:type ::prepare-error
                          :statement q}
                         "xQuery prepare failed"))))))

(defn batch
  "Takes a sequence of statements to be executed in batch.
  By default LOGGED, you can specify :logged :unlogged :counter as an
  optional second argument to control the type.  It also accepts an
  optional third argument, codec instance (see `execute`)"
  ([qs] (batch qs :logged))
  ([qs type]
   (batch qs type default-codec/codec))
  ([qs type codec]
   ;; note that the default BatchStatement impl is now immutable
   (reduce
    (fn [^BatchStatement bs q]
      (.add bs (query->statement q nil codec)))
    (BatchStatement/newInstance (enum/batch-type type))
    qs)))

(defn ^:no-doc ^Statement set-statement-options!
  [^Statement statement
   {:keys [consistency-level
           execution-profile
           execution-profile-name
           idempotent?
           node
           page-size
           paging-state
           query-timestamp
           routing-key
           routing-keyspace
           routing-token
           serial-consistency-level
           timeout
           tracing?]
    :as opts}]

  ;; note Statement objects are immutable now

  (cond-> statement

    (some? consistency-level)
    (.setConsistencyLevel (enum/consistency-level consistency-level))

    (some? execution-profile)
    (.setExecutionProfile execution-profile)

    (some? execution-profile-name)
    (.setExecutionProfileName execution-profile-name)

    (some? idempotent?)
    (.setIdempotent idempotent?)

    (some? node)
    (.setNode node)

    (some? page-size)
    (.setPageSize page-size)

    (some? paging-state)
    (.setPagingState ^ByteBuffer paging-state)

    (some? query-timestamp)
    (.setQueryTimestamp query-timestamp)

    (some? routing-key)
    (.setRoutingKey ^ByteBuffer routing-key)

    (some? routing-keyspace)
    (.setRoutingKeyspace ^String routing-keyspace)

    (some? routing-token)
    (.setRoutingToken routing-token)

    (some? serial-consistency-level)
    (.setSerialConsistencyLevel (enum/consistency-level serial-consistency-level))

    (some? timeout)
    (.setTimeout timeout)

    (some? tracing?)
    (.setTracing tracing?)))

(defn execute
  "Executes a query against a `CqlSession`.
  Returns a collection of rows.

  The query can be a raw string, a PreparedStatement (returned by
  `prepare`) with values passed via the `:values` option key will be bound by
  `execute`, BoundStatement (returned by `qbits.alia/bind`).

  The following `com.datastax.oss.driver.api.core.cql.Statement` options
  are supported:

  * `:values` : values to be bound to a prepared query
  * `:consistency-level` : Keyword, consistency
  * `:execution-profile` : `com.datastax.oss.driver.api.core.config.DriverExecutionProfile`
    instance
  * `:execution-profile-name` : `String`, execution profile name
  * `:idempotent?` : `Boolean`, Whether this statement is idempotent, i.e. whether
    it can be applied multiple times without changing the result beyond
    the initial application
  * `:node` : `com.datastax.oss.driver.api.core.metadata.Node` instance,
    the node to handle the query
  * `:page-size` : Number, sets the number of rows in each page of the results
  * `:paging-state` : `com.datastax.oss.driver.api.core.cql.PagingState` or
    `ByteBuffer` instance. This will cause the next execution of this statement
    to fetch results from a given page, rather than restarting from the
    beginning
  * `:query-timestamp` : Number, sets the timestamp for query
    (if not specified in CQL)
  * `:routing-key` : `ByteBuffer`, the key to use for token-aware routing
  * `:routing-keyspace` : `com.datastax.oss.driver.api.core.CqlIdentifier` or
     `String` instance, setting the keyspace for token-aware routing
  * `:routing-token` : `com.datastax.oss.driver.api.core.metadata.token.Token`,
    the token to use for token-aware routing
  * `:serial-consistency-level` : Keyword, consistency
  * `:timeout` : `java.time.Duration`, the read timeout
  * `:tracing?` : `Bool`, toggles query tracing (available via query result
    metadata)

  and these additional options specify how results are to be handled:

  * `:result-set-fn` : Defaults to `clojure.core/seq` By default a
    result-set is an unchunked lazy seq, you can control this using this
    option. the `:result-set-fn` will be passed a version of the java-driver
    `com.datastax.oss.driver.api.core.cql.ResultSet` object
    which supports `Iterable` and `IReduceInit` giving you full control
    over how the resultset is formed (chunked,
    unchunked, eager or not, etc). A common use is to pass `#(into [] %)`
    as result-set-fn, you then get an eager value, with minimal copies,
    no intermediary seq and potentially better performance. This can be
    very powerfull when used right (for instance with transducers
    `#(into [] xform %))`.
  * `:row-generator` : implements `qbits.alia.result-set/RowGenerator`,
    Defaults to `qbits.alia.codec/row-gen->map` : A RowGenerator dictates
    how we construct rows.
  * `:codec` : map of `:encoder` `:decoder` functions that control how to
    apply extra modifications on data sent/received (defaults to
    `qbits.alia.codec/default`).

  Possible values for consistency:

   `:all` `:any` `:each-quorum` `:local-one` `:local-quorum` `:local-serial`
   `:one` `:quorum` `:serial` `:three` `:two`
   "
  ([^CqlSession session query {:keys [values
                                      codec
                                      result-set-fn
                                      row-generator]
                               :as opts}]
   (let [codec (or codec default-codec/codec)
         ^Statement statement (query->statement query values codec)
         statement (set-statement-options! statement opts)]
     (try

       (result-set/result-set (.execute session statement)
                              result-set-fn
                              row-generator
                              codec)
       (catch Exception err
         (throw (err/ex->ex-info err {:statement statement :values values}))))))
  ;; to support old syle api with unrolled args
  ([^CqlSession session query]
   (execute session query {})))

(defn execute-async
  "Same args as `execute` but executes async and returns a
   `CompletableFuture<AliaAsyncResultSetPage>`

   to fetch and decode the next page use
   `qbits.alia.result-set/fetch-next-page` on the `AliaAsyncResultSetPage`"
  ([^CqlSession session query {:keys [values
                                      codec
                                      result-set-fn
                                      row-generator
                                      executor]
                               :as opts}]
   (try
     (let [codec (or codec default-codec/codec)
           ^Statement statement (query->statement query values codec)
           statement (set-statement-options! statement opts)
           ^CompletionStage async-result-set-cs (.executeAsync session statement)]

       (result-set/handle-async-result-set-completion-stage
        async-result-set-cs
        (assoc opts
               :codec codec
               :statement statement)))

     (catch Exception ex
       (cf/failed-future
        (err/ex->ex-info ex {:statement query :values values})))))

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
