(ns qbits.alia.spec
  "clj/spec for alia
  TODO : * aliases
         * move to separate module"
  (:require
   [clojure.spec :as s]
   [qbits.alia :as alia]
   [qbits.alia.cluster-options :as cluster-options]
   [qbits.alia.enum :as enum]
   [qbits.alia.codec :as codec])
  (:import
   (com.datastax.driver.core
    BatchStatement
    BoundStatement
    CloseFuture
    Cluster
    PreparedStatement
    NettyOptions
    PagingState
    Statement
    ResultSetFuture
    Session
    SSLOptions
    Statement
    TimestampGenerator)
   (com.datastax.driver.core.policies
    AddressTranslator
    LoadBalancingPolicy
    ReconnectionPolicy
    RetryPolicy
    SpeculativeExecutionPolicy)
   (java.nio ByteBuffer)
   (java.util.concurrent Executor)))

(def instance-pred #(partial instance? %))
(def satisfies-pred #(partial satisfies? %))

(defn enum-pred [enum-fn]
  (fn [x]
    (try
      (enum-fn x)
      (catch clojure.lang.ExceptionInfo ei
        (case (some-> ei ex-data :type)
          :qbits.commons.enum/invalid-enum-value false
          (throw ei))
        false))))

(s/def ::alia/cluster (instance-pred Cluster))
(s/def ::alia/session (instance-pred Session))
(s/def ::alia/query (satisfies-pred alia/PStatement))
(s/def ::alia/prepared-statement (instance-pred PreparedStatement))

;; enums
(s/def ::enum/host-distance (enum-pred enum/host-distance))
(s/def ::enum/write-type (enum-pred enum/write-type))
(s/def ::enum/consistency-level (enum-pred enum/consistency-level))
(s/def ::enum/compression (enum-pred enum/compression))
(s/def ::enum/batch-statement-type (enum-pred enum/batch-statement-type))

(s/def ::string string?)

;; cluster opts

(s/def ::cluster-options/contact-points
  (s/or :host string?
        :hosts (s/coll-of string?
                          :min-count 1)))

(s/def ::cluster-options/port #(s/int-in-range? 1024 65535 %))

(s/def ::cluster-options/load-balancing-policy
  (instance-pred LoadBalancingPolicy))
(s/def ::cluster-options/reconnection-policy
  (instance-pred ReconnectionPolicy))

(s/def ::cluster-options/retry-policy
  (instance-pred RetryPolicy))

(s/def ::cluster-options/speculative-execution-policy
  (instance-pred SpeculativeExecutionPolicy))

;; pooling opts

(s/def ::cluster-options/pooling-option
  (s/+ (s/tuple ::enum/host-distance pos-int?)))

(create-ns 'qbits.alia.cluster-options.pooling-options)
(alias 'cluster-options.pooling-options 'qbits.alia.cluster-options.pooling-options)
(s/def ::cluster-options.pooling-options/core-connections-per-host
  ::cluster-options/pooling-option)

(s/def ::cluster-options.pooling-options/max-connections-per-host
  ::cluster-options/pooling-option)
(s/def ::cluster-options.pooling-options/connectoin-thresholds
  ::cluster-options/pooling-option)

(s/def ::cluster-options/pooling-options
  (s/keys :opt-un
          [::cluster-options.pooling-options/core-connections-per-host
           ::cluster-options.pooling-options/max-connections-per-host
           ::cluster-options.pooling-options/connection-thresholds]))

;; socket options
(create-ns 'qbits.alia.cluster-options.socket-options)
(alias 'cluster-options.socket-options 'qbits.alia.cluster-options.socket-options)
(s/def ::cluster-options.socket-options/read-timeout pos-int?)
(s/def ::cluster-options.socket-options/read-timeout pos-int?)
(s/def ::cluster-options.socket-options/receive-buffer-size pos-int?)
(s/def ::cluster-options.socket-options/send-buffer-size pos-int?)
(s/def ::cluster-options.socket-options/so-linger pos-int?)
(s/def ::cluster-options.socket-options/tcp-no-delay? boolean?)
(s/def ::cluster-options.socket-options/reuse-address? boolean?)
(s/def ::cluster-options.socket-options/keep-alive? boolean?)
(s/def ::cluster-options/socket-options
  (s/keys :opt-un
          [::cluster-options.socket-options/connect-timeout
           ::cluster-options.socket-options/read-timeout
           ::cluster-options.socket-options/receive-buffer-size
           ::cluster-options.socket-options/send-buffer-size
           ::cluster-options.socket-options/so-linger
           ::cluster-options.socket-options/tcp-no-delay?
           ::cluster-options.socket-options/reuse-address?
           ::cluster-options.socket-options/keep-alive?]))

;; query opts
(create-ns 'qbits.alia.cluster-options.query-options)
(alias 'cluster-options.query-options 'qbits.alia.cluster-options.query-options)
(s/def ::cluster-options.query-options/fetch-size pos-int?)
(s/def ::cluster-options.query-options/consitency
  (enum-pred enum/consistency-level))
(s/def ::cluster-options.query-options/serial-consistency
  (enum-pred enum/consistency-level))

(s/def ::cluster-options/query-options
  (s/keys :opt-un
          [::cluster-options.query-options/fetch-size
           ::cluster-options.query-options/consistency
           ::cluster-options.query-options/serial-consistency]))

(s/def ::cluster-options/metrics? boolean?)
(s/def ::cluster-options/jmx-reporting? boolean?)

;; credentials
(create-ns 'qbits.alia.cluster-options.credentials)
(alias 'cluster-options.credentials 'qbits.alia.cluster-options.credentials)
(s/def ::cluster-options.credentials/user string?)
(s/def ::cluster-options.credentials/password string?)
(s/def ::cluster-options/credentials
  (s/keys :req-un
          [::cluster-options.credentials/user
           ::cluster-options.credentials/password]))

(s/def ::cluster-options/kerberos? boolean?)
(s/def ::cluster-options/compression :qbits.alia.enum/compression)
(s/def ::cluster-options/ssl? boolean?)

;; ssl options
(create-ns 'qbits.alia.cluster-options.ssl-options)
(alias 'cluster-options.ssl-options 'qbits.alia.cluster-options.ssl-options)

(s/def ::cluster-options.ssl-options/keystore-path string?)
(s/def ::cluster-options.ssl-options/keystore-password string?)
(s/def ::cluster-options.ssl-options/cipher-suites (s/coll-of string? :min-count 1))

(s/def ::cluster-options/ssl-options
  (s/or :ssl-options-instance (instance-pred SSLOptions)
        :ssl-options-map
        (s/keys :opt-un
                [:qbits.alia.ssl-options/keystore-path
                 :qbits.alia.ssl-options/keystore-password
                 :qbits.alia.ssl-options/cipher-suites])))

(s/def ::cluster-options/timestamp-generator
  (instance-pred TimestampGenerator))
(s/def ::cluster-options/address-translator
  (instance-pred AddressTranslator))
(s/def ::cluster-options/netty-options
  (instance-pred NettyOptions))
(s/def ::cluster-options/max-schema-agreement-wait-seconds pos-int?)
(s/def ::cluster-options/cluster-name string?)

(s/def ::alia/cluster-options
  (s/keys
   :req-un [::cluster-options/contact-points]
   :opt-un [::cluster-options/port
            ::cluster-options/load-balancing-policy
            ::cluster-options/reconnection-policy
            ::cluster-options/retry-policy
            ::cluster-options/speculative-execution-policy
            ::cluster-options/pooling-options
            ::cluster-options/socket-options
            ::cluster-options/query-options
            ::cluster-options/metrics?
            ::cluster-options/jmx-reporting?
            ::cluster-options/credentials
            ::cluster-options/kerberos?
            ::cluster-options/compression
            ::cluster-options/ssl?
            ::cluster-options/compression
            ::cluster-options/ssl-options
            ::cluster-options/timestamp-generator
            ::cluster-options/address-translator
            ::cluster-options/netty-options
            ::cluster-options/max-schema-agreement-wait-seconds
            ::cluster-options/cluster-name]))

;; execute & co
(create-ns 'qbits.alia.statement-options)
(alias 'alia.statement-options 'qbits.alia.statement-options)
(s/def ::alia.statement-options/routing-key (instance-pred ByteBuffer))
(s/def ::alia.statement-options/retry-policy (instance-pred RetryPolicy))
(s/def ::alia.statement-options/tracing boolean?)
(s/def ::alia.statement-options/idempotent? boolean?)

(s/def ::alia.statement-options/consistency (enum-pred enum/consistency-level))
(s/def ::alia.statement-options/serial-consistency (enum-pred enum/consistency-level))
(s/def ::alia.statement-options/fetch-size pos-int?)
(s/def ::alia.statement-options/timesamp pos-int?)
(s/def ::alia.statement-options/paging-state (instance-pred PagingState))
(s/def ::alia.statement-options/read-timeout pos-int?)

(s/def ::alia/statement-options
  (s/keys :opts-un
          [::alia.statement-options/routing-key
           ::alia.statement-options/retry-policy
           ::alia.statement-options/tracing?
           ::alia.statement-options/idempotent?
           ::alia.statement-options/consistency
           ::alia.statement-options/serial-consistency
           ::alia.statement-options/fetch-size
           ::alia.statement-options/timesamp
           ::alia.statement-options/paging-state
           ::alia.statement-options/read-timeout]))

(create-ns 'qbits.alia.execute-opts)
(alias 'alia.execute-opts 'qbits.alia.execute-opts)

(s/def ::alia.execute-opts/values
  (s/or :named-values (s/map-of keyword? (satisfies-pred codec/PCodec) :min-count 1)
        :positional-values (s/+ (satisfies-pred codec/PCodec))))


;; TODO refine this one
(s/def ::alia.execute-opts/result-set-fn fn?)

(s/def ::alia.execute-opts/key-fn
  (s/fspec :args (s/cat :column string?)
           :ret any?))

(s/def ::alia/execute-opts-common
  (s/keys :opts-un
          [::alia.execute-opts/values
           ::alia.execute-opts/result-set-fn
           ::alia.execute-opts/key-fn]))

(s/def ::alia/execute-opts
  (s/merge ::alia/execute-opts-common
           ::alia/statement-options))

(create-ns 'qbits.alia.execute-async)
(alias 'alia.execute-async 'qbits.alia.execute-async)
(s/def ::alia.execute-async/executor (instance-pred Executor))
;; TODO refine these 2
(s/def ::alia.execute-async/success fn?)
(s/def ::alia.execute-async/error fn?)

(s/def ::alia/execute-async-opts
  (s/merge ::alia/execute-opts
           (s/keys :opt-un [::alia.execute-async/success
                            ::alia.execute-async/error
                            ::alia.execute-async/executor])))


;; functions

(s/fdef qbits.alia/cluster
  :args (s/cat :cluster-options ::alia/cluster-options)
  :ret ::alia/cluster)

(s/fdef qbits.alia/connect
        :args (s/cat :cluster ::alia/cluster
                     :keyspace (s/? string?))
        :ret ::alia/session)

(s/fdef qbits.alia/shutdown
        :args (s/cat :cluster-or-session
                     (s/or ::alia/cluster ::alia/session))
        :ret (instance-pred CloseFuture))

(s/fdef qbits.alia/bind
        :args (s/cat :statement ::alia/prepared-statement
                     :values ::alia.execute-opts/values)
        :ret (instance-pred BoundStatement))

(s/fdef qbits.alia/prepare
        :args (s/cat :session ::alia/session
                     :query ::alia/query)
        :ret ::alia/prepared-statement)

(s/fdef qbits.alia/batch
        :args (s/cat :statements (s/+ ::alia/query)
                     :type (s/? (enum-pred enum/batch-statement-type)))
        :ret (instance-pred BatchStatement))

(s/fdef qbits.alia/execute
        :args (s/cat :session ::alia/session
                     :query ::alia/query
                     :options (s/? ::alia/execute-opts))
        :ret any?)

(s/fdef qbits.alia/execute-async
        :args (s/cat :session ::alia/session
                     :query ::alia/query
                     :options (s/? ::alia/execute-async-opts))
        :ret (instance-pred ResultSetFuture))
