(ns qbits.alia
  (:require
   [qbits.alia.codec.default :as default-codec]
   [qbits.alia.completable-future :as cf]
   [qbits.alia.cql-session :as cql-session]
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
  "shortcut to create a session-builder and build a session"
  ([] (session {}))
  ([config]
   (let [^CqlSessionBuilder sb (cql-session/cql-session-builder config)]
     (.build sb))))

(defn shutdown
  [^CqlSession session]
  (.close session))

(defn shutdown-async
  [^CqlSession session]
  (.closeAsync session))

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
            (if (instance? ExecutionException ex)
              (.getCause ex)
              ex)))
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
           (settable-by-name/set-named-parameter!
            builder
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
   error callbacks and prepares a statement asynchronously.

   returns CompletionState<PreparedStatement>"
  [^CqlSession session query {:keys [executor] :as opts}]
  (let [^SimpleStatement q (query->statement query nil nil)]
    (try
      (.prepareAsync session q)
      (catch Exception ex
        (cf/failed-future
         (ex->ex-info ex
                      {:type ::prepare-error
                       :query q}
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
   (let [bs (BatchStatement/newInstance (enum/batch-type type))]
     (doseq [q qs]
       (.add bs (query->statement q nil codec)))
     bs)))

(defn ^:no-doc set-statement-options!
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
  (when (some? consistency-level)
    (.setConsistencyLevel statement (enum/consistency-level consistency-level)))
  (when (some? execution-profile)
    (.setExecutionProfile statement execution-profile))
  (when (some? execution-profile-name)
    (.setExecutionProfileName statement execution-profile-name))
  (when (some? idempotent?)
    (.setIdempotent statement idempotent?))
  (when (some? node)
    (.setNode statement node))
  (when (some? page-size)
    (.setPageSize statement page-size))
  (when (some? paging-state)
    (.setPagingState statement paging-state))
  (when (some? query-timestamp)
    (.setQueryTimestamp statement query-timestamp))
  (when (some? routing-key)
    (.setRoutingKey ^SimpleStatement statement
                    ^ByteBuffer routing-key))
  (when (some? routing-keyspace)
    (.setRoutingKeyspace ^SimpleStatement statement
                         ^String routing-keyspace))
  (when (some? routing-token)
    (.setRoutingToken statement routing-token))
  (when (some? serial-consistency-level)
    (.setSerialConsistencyLevel
     statement
     (enum/consistency-level serial-consistency-level)))
  (when (some? timeout)
    (.setTimeout statement timeout))
  (when (some? tracing?)
    (.setTracing statement tracing?)))

(defn execute
  "Executes a query against a session.
  Returns a collection of rows.

  The query can be a raw string, a PreparedStatement (returned by
  `prepare`) with values passed via the `:values` option key will be bound by
  `execute`, BoundStatement (returned by `qbits.alia/bind`).

  The following options are supported:

* `:values` : values to be bound to a prepared query
  * `:consistency-level` : Keyword, consistency
  * `:serial-consistency-level` : Keyword, consistency
* `:routing-key` : ByteBuffer
  * `:routing-keyspace` : ByteBuffer
  * `:routing-token` : ByteBuffer
* `:tracing?` : Bool, toggles query tracing (available via query result metadata)
  * `:page-size` : Number, sets query fetching size
  * `:query-timestamp` : Number, sets the timestamp for query (if not specified in CQL)
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
* `:timeout` : Read timeout in milliseconds

  Possible values for consistency:

:all :any :each-quorum :local-one :local-quorum :local-serial :one :quorum
:serial :three :two"
  ([^CqlSession session query {:keys [values
                                      codec
                                      result-set-fn
                                      row-generator]
                               :as opts}]
   (let [codec (or codec default-codec/codec)
         ^Statement statement (query->statement query values codec)]
     (set-statement-options! statement opts)
     (try

       (result-set/result-set (.execute session statement)
                              result-set-fn
                              row-generator
                              codec)
       (catch Exception err
         (throw (ex->ex-info err {:query statement :values values}))))))
  ;; to support old syle api with unrolled args
  ([^CqlSession session query]
   (execute session query {})))

(defn handle-async-result-set-completion-stage
  "if successful, applies the row-generator to the current page
   if failed, decorates the exception with query and value details"
  [^CompletionStage completion-stage
   {:keys [next-page-handler
           row-generator
           codec
           statement
           values
           executor]
    :as opts}]

  (cf/handle-completion-stage
   completion-stage

   (fn [async-result-set]
     (result-set/async-result-set
      async-result-set
      row-generator
      codec
      next-page-handler))

   (fn [err]
     (throw
      (ex->ex-info err {:query statement :values values})))

   opts))

(defn execute-async
  "Same args as execute but executes async and returns a
   CompletableFuture<{:current-page <records>
                      :async-result-set <async-result-set>
                      :next-page-handler <handler>}>

   to fetch and decode the next page do
   (next-page-handler (.fetchNextPage async-result-set))"
  ([^CqlSession session query {:keys [values
                                      codec
                                      executor]
                               :as opts}]
   (try
     (let [codec (or codec default-codec/codec)
           ^Statement statement (query->statement query values codec)]
       (set-statement-options! statement opts)

       (let [handler (fn arscs-handler
                       [completion-stage]
                       (handle-async-result-set-completion-stage
                        completion-stage
                        (assoc opts
                               :codec codec
                               :statement statement
                               :next-page-handler arscs-handler)))
             ^CompletionStage async-result-set-cs (.executeAsync session statement)]
         (handler async-result-set-cs)))
     (catch Exception ex
       (cf/failed-future
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
