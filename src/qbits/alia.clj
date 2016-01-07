(ns qbits.alia
  (:require
   [qbits.alia.codec :as codec]
   [qbits.alia.codec.udt]
   [qbits.alia.codec.tuple]
   [qbits.commons.ns :as nsq]
   [qbits.alia.utils :as utils]
   [qbits.alia.enum :as enum]
   [qbits.hayt :as hayt]
   [clojure.core.async :as async]
   [qbits.alia.cluster-options :as copt])
  (:import
   (com.datastax.driver.core
    BatchStatement
    BoundStatement
    Cluster
    Cluster$Builder
    LatencyTracker
    PreparedStatement
    Statement
    ResultSet
    ResultSetFuture
    Session
    SimpleStatement
    Statement
    ColumnDefinitions$Definition)
   (com.google.common.util.concurrent
    MoreExecutors
    Futures
    FutureCallback)
   (java.nio ByteBuffer)))

(defn ^:no-doc get-executor
  [x]
  (or x (MoreExecutors/sameThreadExecutor)))

(defn cluster
  "Takes an option map and returns a new
com.datastax.driver.core/Cluster instance.

The following options are supported:

* `:contact-points` : List of nodes ip addresses to connect to.

* `:port` : port to connect to on the nodes (native transport must be
  active on the nodes: `start_native_transport: true` in
  cassandra.yaml). Defaults to 9042 if not supplied.

* `:load-balancing-policy` : Configure the
  [Load Balancing Policy](http://mpenet.github.io/alia/qbits.alia.policy.load-balancing.html)
  to use for the new cluster.

* `:reconnection-policy` : Configure the
  [Reconnection Policy](http://mpenet.github.io/alia/qbits.alia.policy.reconnection.html)
  to use for the new cluster.

* `:retry-policy` : Configure the
  [Retry Policy](http://mpenet.github.io/alia/qbits.alia.policy.retry.html)
  to use for the new cluster.

* `:speculative-execution` The policy that decides if the driver will
  send speculative queries to the next hosts when the current host
  takes too long to respond. [Speculative Execution
  Policy](http://mpenet.github.io/alia/qbits.alia.policy.speculative-execution.html)

* `:metrics?` : Toggles metrics collection for the created cluster
  (metrics are enabled by default otherwise).

* `:jmx-reporting?` : Toggles JMX reporting of the metrics.

* `:credentials` : Takes a map of :user and :password for use with
  Cassandra's PasswordAuthenticator

* `:compression` : Compression supported by the Cassandra binary
  protocol. Can be `:none`, `:snappy` or `:lz4`.

* `:cluster-name` : Optional name for create cluster

* `max-schema-aggreement-wait-seconds` Sets the maximum time to wait
  for schema agreement before returning from a DDL query.

* `:netty-options`: (advanced) see
  http://docs.datastax.com/en/drivers/java/2.1/com/datastax/driver/core/NettyOptions.html

* `:address-translator`: Configures the address translator to use for
  the new cluster. Expects
  a [AddressTranslator](http://mpenet.github.io/alia/qbits.alia.policy.address-translator.html)
  or you can pass :ec2-multi-region which would translate in the
  underlying implementations.

* `:timestamp-generator`: Configures the timestamp generator to use
  with the new cluster. Expects
  a [timestamp-generator](http://mpenet.github.io/alia/qbits.alia.timestamp-generator.html)
  instance, or `:atomic-monotonic` , `:server-side` or `:thread-local`
  which would translate in the underlying implementations.

* `:ssl?`: enables/disables SSL

* `:ssl-options` : advanced SSL setup using a
  `com.datastax.driver.core.SSLOptions` instance or a map of
  `:keystore-path`, `:keystore-password` and optional
  `:cipher-suites`.  This provides a path/pwd to a
  [KeyStore](http://docs.oracle.com/javase/7/docs/api/java/security/KeyStore.html)
  that can ben generated
  with [keytool](http://docs.oracle.com/javase/7/docs/technotes/tools/solaris/keytool.html)
  Overriding default cipher suites is supported via `:cipher-suites`,
  which accepts a sequence of Strings.

* `:kerberos?` : activate Kerberos via DseAuthProvider, see
  http://www.datastax.com/dev/blog/accessing-secure-dse-clusters-with-cql-native-protocol

* `:pooling-options` : The pooling options used by this builder.
  Options related to connection pooling.

  The driver uses connections in an asynchronous way. Meaning that
  multiple requests can be submitted on the same connection at the
  same time. This means that the driver only needs to maintain a
  relatively small number of connections to each Cassandra host. These
  options allow to control how many connections are kept exactly.

  For each host, the driver keeps a core amount of connections open at
  all time. If the utilisation of those connections reaches a
  configurable threshold ,more connections are created up to a
  configurable maximum number of connections.

  Once more than core connections have been created, connections in
  excess are reclaimed if the utilisation of opened connections drops
  below the configured threshold.

  Each of these parameters can be separately set for `:local` and `:remote`
  hosts (HostDistance). For `:ignored` hosts, the default for all those
  settings is 0 and cannot be changed.

  Each of the following configuration keys, take a map of {distance value}  :
  ex:
  ```clojure
  :core-connections-per-host {:remote 10 :local 100}
  ```

  + `:core-connections-per-host` Number
  + `:max-connections-per-host` Number
  + `:max-simultaneous-requests-per-connection` Number
  + `:min-simultaneous-requests-per-connection` Number

* `:socket-options`: a map of
    - `:connect-timeout-millis` Number
    - `:read-timeout-millis` Number
    - `:receive-buffer-size` Number
    - `:send-buffer-size` Number
    - `:so-linger` Number
    - `:tcp-no-delay?` Bool
    - `:reuse-address?` Bool
    - `:keep-alive?` Bool

* `:query-options`: a map of
    - `:fetch-size` Number
    - `:consistency` (consistency Keyword)
    - `:serial-consistency` (consistency Keyword)

* `:jmx-reporting?` Bool, enables/disables JMX reporting of the metrics.


The handling of these options is achieved with a multimethod that you
could extend if you need to handle some special case or want to create
your own options templates.
See `qbits.alia.cluster-options/set-cluster-option!` [source](../src/qbits/alia/cluster_options.clj#L19)


Values for consistency:

:all :any :each-quorum :local-one :local-quorum :local-serial :one :quorum
:serial :three :two
"
  ([options]
     (-> (Cluster/builder)
         (copt/set-cluster-options! (merge {:contact-points ["localhost"]}
                                           options))
         .build))
  ([] (cluster {})))

(defn ^Session connect
  "Returns a new com.datastax.driver.core/Session instance. We need to
have this separate in order to allow users to connect to multiple
keyspaces from a single cluster instance"
  ([^Cluster cluster keyspace]
     (.connect cluster (name keyspace)))
  ([^Cluster cluster]
     (.connect cluster)))

(defn shutdown
  "Shutdowns Session or Cluster instance, clearing the underlying
pools/connections"
  [x]
  (cond
    (instance? Session x)
    (.closeAsync ^Session x)
    (instance? Cluster x)
    (.closeAsync ^Cluster x)))

(defn ^:no-doc ex->ex-info
  ([^Exception ex data msg]
     (ex-info msg
              (merge {:type ::execute
                      :exception ex}
                     data)
              (.getCause ex)))
  ([ex data]
     (ex->ex-info ex data "Query execution failed")))

(defn prepare
  "Takes a session and a query (raw string or hayt) and returns a
  com.datastax.driver.core.PreparedStatement instance to be used in
  `execute` after it's been bound with `bind`. Hayt query parameter
  will be compiled with qbits.hayt/->raw internaly
  ex: (prepare session (select :foo (where {:bar ?})))"
  [^Session session query]
  (let [^String q (if (map? query)
                    (hayt/->raw query)
                    query)]
    (try
      (.prepare session q)
      (catch Exception ex
        (throw (ex->ex-info ex
                            {:type ::prepare-error
                             :query q}
                            "Query prepare failed"))))))

(defn bind
  "Takes a statement and a collection of values and returns a
  com.datastax.driver.core.BoundStatement instance to be used with
  `execute` (or one of its variants)

   Where values:
     Map: for named bindings       (i.e. INSERT INTO table (id, date) VALUES (:id :date))
     List: for positional bindings (i.e. INSERT INTO table (id, date) VALUES (?, ?))"
  [^PreparedStatement statement values]
  (try
    (if (map? values)
      (let [bound (.bind statement)]
        (doseq [^ColumnDefinitions$Definition column (.getVariables statement)]
          (let [binding-name (.getName column)]
            (codec/set-named-parameter! bound binding-name (codec/encode (get values (keyword binding-name))))))
        bound)
      (.bind statement (to-array (map codec/encode values))))
    (catch Exception ex
      (throw (ex->ex-info ex {:query statement
                              :type ::bind-error
                              :values values}
                          "Query binding failed")))))

(defprotocol ^:no-doc PStatement
  (^:no-doc query->statement
    [q values] "Encodes input into a Statement instance"))

(extend-protocol PStatement
  Statement
  (query->statement [q _] q)

  PreparedStatement
  (query->statement [q values]
    (bind q values))

  String
  (query->statement [q values]
    (SimpleStatement. q (to-array (map codec/encode values))))

  BatchStatement
  (query->statement [bs values]
    (when values
      (throw (ex-info {:type ::bind-error}
                      "You cannot bind values to batch statements directly,
               if you need to do so use qbits.alia/bind on your statements
               separately")))
    bs)

  clojure.lang.IPersistentMap
  (query->statement [q values]
    (query->statement (hayt/->raw q) values)))

(defn batch
  "Takes a sequence of statements to be executed in batch.
By default LOGGED, you can specify :logged :unlogged :counter as
  an optional second argument to control the type"
  ([qs] (batch qs :logged))
  ([qs type]
   (let [bs (BatchStatement. (enum/batch-statement-type type))]
     (doseq [q qs]
       (.add bs (query->statement q nil)))
     bs)))

(defn ^:no-doc set-statement-options!
  [^Statement statement routing-key retry-policy tracing? idempotent?
   consistency serial-consistency fetch-size timestamp paging-state]
  (when routing-key
    (.setRoutingKey ^SimpleStatement statement
                    ^ByteBuffer routing-key))
  (when retry-policy
    (.setRetryPolicy statement retry-policy))
  (when tracing?
    (.enableTracing statement))
  (when idempotent?
    (.setIdempotent statement idempotent?))
  (when fetch-size
    (.setFetchSize statement fetch-size))
  (when timestamp
    (.setDefaultTimestamp statement timestamp))
  (when paging-state
    (.setPagingState statement paging-state))
  (when serial-consistency
    (.setSerialConsistencyLevel statement
                                (enum/consistency-level serial-consistency)))
  (when consistency
    (.setConsistencyLevel statement (enum/consistency-level consistency))))

(defn execute
  "Executes a query against a session.
Returns a collection of rows.

The query can be a raw string, a PreparedStatement (returned by
`prepare`) with values passed via the `:values` option key will be bound by
`execute`, BoundStatement (returned by `qbits.alia/bind`), or a Hayt query.

The following options are supported:

* `:values` : values to be bound to a prepared query
* `:consistency` : Keyword, consistency
* `:serial-consistency` : Keyword, consistency
* `:routing-key` : ByteBuffer
* `:retry-policy` : one of qbits.alia.policy.retry/*
* `:tracing?` : Bool, toggles query tracing (available via query result metadata)
* `:key-fn` : fn applied on keys (they are keyword by default)
* `:fetch-size` : Number, sets query fetching size
* `:timestamp` : Number, sets the timestamp for query (if not specified in CQL)
* `:idempotent?` : Whether this statement is idempotent, i.e. whether
  it can be applied multiple times without changing the result beyond
  the initial application.
* `:paging-state` : Expects a com.datastax.driver.core.PagingState
  instance. This will cause the next execution of this statement to
  fetch results from a given page, rather than restarting from the
  beginning

Values for consistency:

:all :any :each-quorum :local-one :local-quorum :local-serial :one :quorum
:serial :three :two"
  ([^Session session query {:keys [consistency serial-consistency
                                   routing-key retry-policy
                                   result-set-fn key-fn
                                   tracing? idempotent? paging-state
                                   fetch-size values timestamp]}]
     (let [^Statement statement (query->statement query values)]
       (set-statement-options! statement routing-key retry-policy
                               tracing? idempotent?
                               consistency serial-consistency fetch-size
                               timestamp paging-state)
       (try
         (codec/result-set->maps (.execute session statement)
                                 result-set-fn
                                 key-fn)
         (catch Exception err
           (throw (ex->ex-info err {:query statement :values values}))))))
  ;; to support old syle api with unrolled args
  ([^Session session query]
   (execute session query {})))

(defn execute-async
  "Same execute but async and takes optional :success and :error
  callback functions via options. For options refer to
  `qbits.alia/execute` doc"
  ([^Session session query {:keys [executor consistency serial-consistency
                                   routing-key retry-policy result-set-fn
                                   tracing? idempotent?
                                   key-fn fetch-size values timestamp
                                   paging-state success error]}]
   (try
     (let [^Statement statement (query->statement query values)]
       (set-statement-options! statement routing-key retry-policy
                               tracing? idempotent?
                               consistency serial-consistency fetch-size
                               timestamp paging-state)
       (let [^ResultSetFuture rs-future (.executeAsync session statement)]
         (Futures/addCallback
           rs-future
           (reify FutureCallback
             (onSuccess [_ result]
               (when success
                 (try
                   (success (codec/result-set->maps (.get rs-future)
                                                    result-set-fn
                                                    key-fn))
                   (catch Exception err
                     (error (ex->ex-info err {:query statement :values values}))))))
             (onFailure [_ ex]
               (when error
                 (error (ex->ex-info ex {:query statement :values values})))))
           (get-executor executor))
         rs-future))
     (catch Throwable t
       (error t))))
  ([^Session session query]
   (execute-async session query {})))

(defn execute-chan
  "Same as execute, but returns a clojure.core.async/promise-chan that is
  wired to the underlying ResultSetFuture. This means this is usable
  with `go` blocks or `take!`. Exceptions are sent to the channel as a
  value, it's your responsability to handle these how you deem
  appropriate.

  For options refer to `qbits.alia/execute` doc"
  ([^Session session query {:keys [executor consistency serial-consistency
                                   routing-key retry-policy result-set-fn
                                   tracing? idempotent?
                                   key-fn fetch-size values timestamp
                                   paging-state]}]
   (let [ch (async/promise-chan)]
     (try
       (let [^Statement statement (query->statement query values)]
         (set-statement-options! statement routing-key retry-policy tracing? idempotent?
                                 consistency serial-consistency fetch-size
                                 timestamp paging-state)
         (let [^ResultSetFuture rs-future (.executeAsync session statement)]
           (Futures/addCallback
            rs-future
            (reify FutureCallback
              (onSuccess [_ result]
                (try
                  (async/put! ch (codec/result-set->maps (.get rs-future)
                                                         result-set-fn
                                                         key-fn))
                  (catch Exception err
                    (async/put! ch (ex->ex-info err {:query statement :values values}))))
                (async/close! ch))
              (onFailure [_ ex]
                (async/put! ch (ex->ex-info ex {:query statement :values values}))))
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
                                   routing-key retry-policy result-set-fn tracing? idempotent?
                                   key-fn fetch-size values timestamp
                                   channel paging-state]}]
   (let [ch (or channel (async/chan (or fetch-size (-> session
                                                       .getCluster
                                                       .getConfiguration
                                                       .getQueryOptions
                                                       .getFetchSize))))]
     (try
       (let [^Statement statement (query->statement query values)]
         (set-statement-options! statement routing-key retry-policy
                                 tracing? idempotent?
                                 consistency serial-consistency fetch-size
                                 timestamp paging-state)
         (let [^ResultSetFuture rs-future (.executeAsync session statement)]
           (Futures/addCallback
            rs-future
            (reify FutureCallback
              (onSuccess [_ result]
                (async/go
                  (try
                    (loop [rows (codec/result-set->maps
                                 (.get ^ResultSetFuture rs-future)
                                 result-set-fn
                                 key-fn)]
                      (when-let [row (first rows)]
                        (when (async/>! ch row)
                          (recur (rest rows)))))
                    (catch Exception err
                      (async/put! ch (ex->ex-info err {:query statement :values values}))))
                  (async/close! ch)))
              (onFailure [_ ex]
                (async/put! ch (ex->ex-info ex {:query statement :values values}))
                (async/close! ch)))
            (get-executor executor))))
       (catch Throwable t
         (async/put! ch t)
         (async/close! ch)))
     ch))
  ([^Session session query]
     (execute-chan-buffered session query {})))

(defn ^:no-doc lazy-query-
  [session query pred coll opts]
  (lazy-cat coll
            (when query
              (let [coll (execute session query opts)]
                (lazy-query- session (pred query coll) pred coll opts)))))

(defn lazy-query
  "Takes a session, a query (hayt, raw or prepared) and a query modifier fn (that
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


(defn register!
  "Register querylogger/latency tracker to cluster"
  [^Cluster cluster ^LatencyTracker latency-tracker]
  (.register cluster latency-tracker))

(defn unregister!
  "Unregister querylogger/latency tracker from cluster"
  [^Cluster cluster ^LatencyTracker latency-tracker]
  (.unregister cluster latency-tracker))

;; custom encoders
(nsq/alias-var 'udt-encoder #'qbits.alia.codec.udt/encoder)
(nsq/alias-var 'tuple-encoder #'qbits.alia.codec.tuple/encoder)
