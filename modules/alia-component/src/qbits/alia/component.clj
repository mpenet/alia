(ns qbits.alia.component
  "Alia module bringing component and spec support.
   As a consumer, a CassandraRegistry record is provided.

   CassandraRegistry instances are responsible for maintaining
   sessions to clusters.

   Downstream consumers of this component can then provide
   a map of query functions and obtain a function which
   performs queries against the correct cluster and optional
   default query options.

   See `query-functions` for details on how to use it."
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha         :as s]
            [qbits.alia                 :as alia]
            qbits.alia.spec))

(defn keyspace-session
  "Retrieve a `Session` instance set to use a specific keyspace.
   keyspace may be a keyword or string."
  [registry k keyspace]
  (if-let [config (get (:configs registry) (keyword k))]
    (alia/session (merge config {:session-keyspace keyspace}))
    (throw (IllegalArgumentException. (str "no such cluster: " k)))))

(defn ->opts
  "This should provide a solid way to parameterize interaction with
   Cassandra. For each named query `nick` we determine what options to
   give to the query in the following way:

   - We fetch defaults from the configuration provided query options
   - We merge code-local defaults for this query
   - We merge query-specific defaults from configuration"
  [nick config code-opts]
  (let [opts (merge (:defaults config) code-opts (get config nick))]
    (s/assert ::alia/execute-opts
              (reduce-kv #(cond-> %1 %3 (assoc %2 %3)) {} opts))))

(defn ->query
  "Get a function which executes a query, preparing the statement."
  [nick session query-opts {:keys [statement opts]}]
  (let [prepared (alia/prepare session statement)
        cfg-opts (->opts nick query-opts opts)]
    (fn [run-opts]
      (let [opts (merge cfg-opts run-opts)
            exec-fn (or (:execute-fn opts) alia/execute)]
        (exec-fn session prepared (dissoc opts :execute-fn))))))

(defn query-fn-map
  "Yield a map of query nickname to function executing the query.
   Provided queries should be statements valid for `alia/prepare`."
  [session queries query-opts]
  (reduce-kv #(assoc %1 %2 (->query %2 session query-opts %3)) {} queries))

(defn cluster-query-opts
  "Fetch query-options from configuration."
  [registry cluster-name]
  (let [globals (:query-opts registry)
        cluster (get-in registry [:cluster-opts cluster-name])]
    (merge globals cluster)))

(defn query-functions
  "Given a cluster, keyspace, and query map, yield a function
   which executes queries by nickname. If no cluster-name
   is provided, assume a single-cluster registry and fetch
   the first from the registry.

   The query function has three arities: 1, 2, and 3.
   The first argument is always the nickname, the optional
   second one is a vector of values to attach to the prepared
   query, the last one additional options to override when
   sending the query.

   Here, options accept an addition `:execute-fn` key which
   determine which function should be used to send the
   query out, defaulting to `alia/execute`.

   Each query can be configured with global configuration defaults,
   code-supplied defaults, and query-specific configuration overrides,
   processed in that order as documented in `query`."
  ([registry cluster-name keyspace queries]
   (let [session (keyspace-session registry cluster-name keyspace)
         opts    (cluster-query-opts registry cluster-name)
         qmap    (query-fn-map session queries opts)]
     (swap! (:sessions registry) conj session)
     (fn execute-query
       ([qname]
        (execute-query qname [] {}))
       ([qname values]
        (execute-query qname values {}))
       ([qname values run-opts]
        (if-let [query-fn (get qmap qname)]
          (query-fn (cond-> run-opts (seq values) (assoc :values values)))
          (throw (IllegalArgumentException. (str "no such query: " qname))))))))
  ([registry keyspace queries]
   (query-functions registry ::unnamed keyspace queries)))

(defrecord CassandraMultiClusterRegistry [cluster-opts
                                          query-opts
                                          configs
                                          clusters
                                          sessions]
  component/Lifecycle
  (start [this]
    (doseq [[_ config] configs]
      (s/assert ::alia/cluster-options config)))
  (stop [this]
    (when (some? sessions)
      (doseq [session @sessions]
        (alia/close session)))
    (assoc this :sessions nil)))

(defn cassandra-registry
  "A single cluster version of CassandraMultiClusterRegistry"
  [cluster-opts query-opts config]
  (map->CassandraMultiClusterRegistry {:cluster-opts {::unnamed cluster-opts}
                                       :query-opts   {::unnamed query-opts}
                                       :configs      {::unnamed config}}))

(defrecord QueryRegistry [query-fn cluster-name keyspace config cassandra]
  component/Lifecycle
  (start [this]
    (let [qfn  (query-functions cassandra (or cluster-name ::unnamed)
                                keyspace config)]
      (assoc this :query-fn qfn)))
  (stop [this]
    (assoc this :query-fn nil)))

(defn query!
  "Convenience function against a QueryRegistry to execute a named query.
   See `query-functions` for the behavior of the query function"
  [{:keys [query-fn]} & args]
  (apply query-fn args))
