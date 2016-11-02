(ns qbits.alia.spec
  "Basic specs for validation/instrumentation, atm this doesn't
  include gen in most cases, not sure it ever will"
  (:require
   [clojure.spec :as s]
   [clojure.spec.gen :as sg]
   [qbits.spex :as x]
   [qbits.spex.networking :as xn]
   [qbits.alia :as alia]
   [qbits.alia.cluster-options :as cluster-options]
   [qbits.alia.enum :as enum]
   [qbits.commons.enum :as ce]
   [qbits.alia.codec :as codec])
  (:import
   (com.datastax.driver.core
    AuthProvider
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
    TimestampGenerator
    UserType
    TupleType
    ;; enums
    BatchStatement$Type
    ConsistencyLevel
    HostDistance
    ProtocolOptions$Compression
    WriteType)
   (com.datastax.driver.core.policies
    AddressTranslator
    LoadBalancingPolicy
    ReconnectionPolicy
    RetryPolicy
    SpeculativeExecutionPolicy)
   (java.nio ByteBuffer)
   (java.util.concurrent Executor)))

(def string-or-named? #(or (string? %) (instance? clojure.lang.Named %)))

(s/def ::alia/cluster (x/instance-of Cluster))
(s/def ::alia/session (x/instance-of Session))
(s/def ::alia/query (x/satisfies alia/PStatement))
(s/def ::alia/prepared-statement (x/instance-of PreparedStatement))

;; enums
(letfn [(enum-key-set [enum] (-> enum ce/enum->map keys set))]
  (s/def ::enum/host-distance (enum-key-set HostDistance))
  (s/def ::enum/write-type (enum-key-set WriteType))
  (s/def ::enum/consistency-level (enum-key-set ConsistencyLevel))
  (s/def ::enum/compression (enum-key-set ProtocolOptions$Compression))
  (s/def ::enum/batch-statement-type (enum-key-set BatchStatement$Type)))


;; cluster opts

(s/def ::cluster-options/contact-points
  (s/coll-of ::xn/hostname :min-count 1))

(s/def ::cluster-options/port ::xn/port)

(s/def ::cluster-options/load-balancing-policy
  (x/instance-of LoadBalancingPolicy))
(s/def ::cluster-options/reconnection-policy
  (x/instance-of ReconnectionPolicy))

(s/def ::cluster-options/retry-policy
  (x/instance-of RetryPolicy))

(s/def ::cluster-options/speculative-execution-policy
  (x/instance-of SpeculativeExecutionPolicy))

;; pooling opts

(s/def ::cluster-options/pooling-option
  (s/+ (s/tuple ::enum/host-distance pos-int?)))

(x/ns-as 'qbits.alia.cluster-options.pooling-options
         'cluster-options.pooling-options)
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
(x/ns-as 'qbits.alia.cluster-options.socket-options
         'cluster-options.socket-options)
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
(x/ns-as 'qbits.alia.cluster-options.query-options
         'cluster-options.query-options)
(s/def ::cluster-options.query-options/fetch-size pos-int?)
(s/def ::cluster-options.query-options/consitency ::enum/consistency-level)
(s/def ::cluster-options.query-options/serial-consistency ::enum/consistency-level)

(s/def ::cluster-options/query-options
  (s/keys :opt-un
          [::cluster-options.query-options/fetch-size
           ::cluster-options.query-options/consistency
           ::cluster-options.query-options/serial-consistency]))

(s/def ::cluster-options/metrics? boolean?)
(s/def ::cluster-options/jmx-reporting? boolean?)

;; credentials
(x/ns-as 'qbits.alia.cluster-options.credentials
         'cluster-options.credentials)
(s/def ::cluster-options.credentials/user string?)
(s/def ::cluster-options.credentials/password string?)
(s/def ::cluster-options/credentials
  (s/keys :req-un
          [::cluster-options.credentials/user
           ::cluster-options.credentials/password]))

(s/def ::cluster-options/auth-provider (x/instance-of AuthProvider))
(s/def ::cluster-options/kerberos? boolean?)
(s/def ::cluster-options/compression ::enum/compression)
(s/def ::cluster-options/ssl? boolean?)

;; ssl options
(x/ns-as 'qbits.alia.cluster-options.ssl-options
         'cluster-options.ssl-options)
(s/def ::cluster-options.ssl-options/keystore-path string?)
(s/def ::cluster-options.ssl-options/keystore-password string?)
(s/def ::cluster-options.ssl-options/cipher-suites (s/coll-of string? :min-count 1))

(s/def ::cluster-options/ssl-options
  (s/or :ssl-options-instance (x/instance-of SSLOptions)
        :ssl-options-map
        (s/keys :opt-un
                [::cluster-options.ssl-options/keystore-path
                 ::cluster-options.ssl-options/keystore-password
                 ::cluster-options.ssl-options/cipher-suites])))

(s/def ::cluster-options/timestamp-generator
  (x/instance-of TimestampGenerator))
(s/def ::cluster-options/address-translator
  (x/instance-of AddressTranslator))
(s/def ::cluster-options/netty-options
  (x/instance-of NettyOptions))
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
            ::cluster-options/auth-provider
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
(s/def ::alia.statement-options/routing-key (x/instance-of ByteBuffer))
(s/def ::alia.statement-options/retry-policy (x/instance-of RetryPolicy))
(s/def ::alia.statement-options/tracing boolean?)
(s/def ::alia.statement-options/idempotent? boolean?)

(s/def ::alia.statement-options/consistency ::enum/consistency-level)
(s/def ::alia.statement-options/serial-consistency ::enum/consistency-level)
(s/def ::alia.statement-options/fetch-size pos-int?)
(s/def ::alia.statement-options/timesamp pos-int?)
(s/def ::alia.statement-options/paging-state (x/instance-of PagingState))
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

(s/def ::encodable (s/spec any?
                         :gen (fn [] (sg/return identity))))
(s/def ::decodable (s/spec any?
                         :gen (fn [] (sg/return identity))))

(s/def ::codec/encoder
  (s/spec (s/fspec :args (s/cat :value ::encodable)
                   :ret any?)
          :gen (sg/return identity)))

(s/def ::codec/decoder
  (s/spec (s/fspec :args (s/cat :value ::decodable)
                   :ret any?)
          :gen (fn [] (sg/return identity))))

(s/def ::alia.execute-opts/codec (s/keys :req-un [::codec/encoder
                                                  ::codec/decoder]))

(s/def ::alia.execute-opts/values
  (s/or :named-values (s/map-of keyword? ::encodable :min-count 1)
        :positional-values (s/+ ::encodable)))

(s/def ::alia.execute-opts/result-set-fn
  (s/fspec :args (s/cat :result-set any?)
           :ret any?))

(s/def ::alia.execute-opts/row-generator (x/satisfies codec/RowGenerator))

(s/def ::alia/execute-opts-common
  (s/keys :opts-un
          [::alia.execute-opts/values
           ::alia.execute-opts/codec
           ::alia.execute-opts/result-set-fn
           ::alia.execute-opts/row-generator]))

(s/def ::alia/execute-opts
  (s/merge ::alia/execute-opts-common
           ::alia/statement-options))

(create-ns 'qbits.alia.execute-async)
(alias 'alia.execute-async 'qbits.alia.execute-async)
(s/def ::alia.execute-async/executor (x/instance-of Executor))

;; TODO fix when we can avoid to run gen on instrument
(comment
  (s/def ::alia.execute-async/success
    (s/fspec :args (s/cat :result-set any?)
             :ret any?))

  (s/def ::alia.execute-async/error
    (s/fspec :args (s/cat :err (x/instance-of clojure.lang.ExceptionInfo))
             :ret any?)))

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
                     (s/or :cluster ::alia/cluster
                           :session ::alia/session))
        :ret (x/instance-of CloseFuture))

(s/fdef qbits.alia/bind
        :args (s/cat :statement ::alia/prepared-statement
                     :values (s/nilable ::alia.execute-opts/values)
                     :codec (s/? ::alia.execute-opts/codec))
        :ret (x/instance-of BoundStatement))

(s/fdef qbits.alia/prepare
        :args (s/cat :session ::alia/session
                     :query ::alia/query)
        :ret ::alia/prepared-statement)

(s/fdef qbits.alia/batch
        :args (s/cat :statements (s/spec (s/+ ::alia/query))
                     :type (s/? ::enum/batch-statement-type)
                     :codec (s/? ::alia.execute-opts/codec))
        :ret (x/instance-of BatchStatement))

(s/fdef qbits.alia/execute
        :args (s/cat :session ::alia/session
                     :query ::alia/query
                     :options (s/? ::alia/execute-opts))
        :ret any?)

(s/fdef qbits.alia/execute-async
        :args (s/cat :session ::alia/session
                     :query ::alia/query
                     :options (s/? ::alia/execute-async-opts))
        :ret (x/instance-of ResultSetFuture))

(s/fdef qbits.alia/udt-encoder
        :args (s/cat :session ::alia/session
                     :keyspace (s/? string-or-named?)
                     :udt-type string-or-named?))

(s/fdef qbits.alia/tuple-encoder
        :args (s/cat :session ::alia/session
                     :keyspace (s/? string-or-named?)
                     :table string-or-named?
                     :column string-or-named?))
