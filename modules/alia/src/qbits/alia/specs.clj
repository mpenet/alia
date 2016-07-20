(ns qbits.alia.specs
  "clj/specs for alia

  TODO : * aliases"
  (:require
   [clojure.spec :as s]
   [qbits.alia :as alia]
   [qbits.alia.cluster-options :as cluster-options]
   [qbits.alia.enum :as enum])
  ;; TODO copy pasta -> cleanup
  (:import
   (com.datastax.driver.core
    BatchStatement
    Cluster
    Cluster$Builder
    LatencyTracker
    PreparedStatement
    NettyOptions
    PagingState
    Statement
    ResultSet
    ResultSetFuture
    Session
    SimpleStatement
    RegularStatement
    SSLOptions
    Statement
    TimestampGenerator)
   (com.datastax.driver.core.policies
    AddressTranslator
    LoadBalancingPolicy
    ReconnectionPolicy
    RetryPolicy
    SpeculativeExecutionPolicy)
   (com.google.common.util.concurrent
    MoreExecutors
    Futures
    FutureCallback)
   (java.nio ByteBuffer)
   (java.util Map)))

(def instance-pred #(partial instance? %))

(defn enum-pred [enum-fn]
  (fn [x]
    (try
      (enum-fn x)
      (catch clojure.lang.ExceptionInfo ei false))))

(s/def ::alia/cluster (instance-pred Cluster))
(s/def ::alia/session (instance-pred Session))
(s/def ::alia/query #(satisfies? alia/PStatement %))

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
  (s/+ (s/cat :distance ::enum/host-distance
              :value pos-int?)))

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

(s/def ::cluster-options
  (s/keys :opt-un
          [::cluster-options/contact-points
           ::cluster-options/port
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
::alia.statement-options/serial-consitency (enum-pred enum/consistency-level)
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
           ::alia.statement-options/serial-consitency
           ::alia.statement-options/fetch-size
           ::alia.statement-options/timesamp
           ::alia.statement-options/paging-state
           ::alia.statement-options/read-timeout]))

;; instrumentation
