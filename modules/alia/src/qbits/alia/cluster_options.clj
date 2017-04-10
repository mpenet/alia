(ns qbits.alia.cluster-options
  (:require
   [qbits.alia.enum :as enum]
   [clojure.java.io :as io]
   [qbits.alia.timestamp-generator :as tsg]
   [qbits.alia.policy.address-translator :as at]
   [qbits.alia.policy.retry :as retry]
   [qbits.alia.policy.load-balancing :as lb]
   [qbits.alia.policy.reconnection :as reconnection]
   [qbits.alia.policy.speculative-execution :as se])
  (:import
   (com.datastax.driver.core
    Cluster$Builder
    PercentileTracker$Builder
    HostDistance
    PoolingOptions
    ProtocolOptions$Compression
    QueryOptions
    SocketOptions
    JdkSSLOptions
    JdkSSLOptions$Builder
    SSLOptions
    TimestampGenerator
    ClusterWidePercentileTracker
    PerHostPercentileTracker)
   (com.datastax.driver.core.policies
    LatencyAwarePolicy
    LatencyAwarePolicy$Builder
    AddressTranslator
    LoadBalancingPolicy
    ReconnectionPolicy
    RetryPolicy
    SpeculativeExecutionPolicy
    Policies)
   (com.datastax.driver.dse.auth
    ;; DsePlainTextAuthProvider
    DseGSSAPIAuthProvider)
   (javax.net.ssl
    TrustManagerFactory
    KeyManagerFactory
    SSLContext)
   (java.security KeyStore)
   (java.net
    InetSocketAddress
    InetAddress)
   (java.util.concurrent TimeUnit)))

(defn socket-address
  [{:keys [ip hostname port]}]
  (cond
    (and hostname port) (InetSocketAddress. ^String hostname ^int (int port))
    (and ip port)       (InetSocketAddress. ^InetAddress (InetAddress/getByName ip)
                                            ^int (int port))
    port                (InetSocketAddress. ^int (int port))))

(defn time-period
  [[value unit]]
  {:value value
   :unit  (-> unit name clojure.string/upper-case TimeUnit/valueOf)})

(defmulti set-cluster-option! (fn [k ^Cluster$Builder builder option] k))

(defmethod set-cluster-option! :contact-points
  [_ builder hosts]
  (.addContactPoints ^Cluster$Builder builder
                     ^"[Ljava.lang.String;"
                     (into-array hosts)))

(defmethod set-cluster-option! :port
  [_ builder port]
  (.withPort ^Cluster$Builder builder (int port)))

(defn retry-policy
  [policy]
  (case policy
    :default              (retry/default-retry-policy)
    :fallthrough          (retry/fallthrough-retry-policy)
    :downgrading          (retry/downgrading-consistency-retry-policy)
    :logging/default     (retry/logging-retry-policy
                          (retry/default-retry-policy))
    :logging/fallthrough (retry/logging-retry-policy
                          (retry/fallthrough-retry-policy))
    :logging/downgrading (retry/logging-retry-policy
                          (retry/downgrading-consistency-retry-policy))))

(defmethod set-cluster-option! :retry-policy
  [_ ^Cluster$Builder builder policy]
  (.withRetryPolicy builder
                    (if (instance? RetryPolicy policy)
                      policy
                      (retry-policy policy))))

(defn latency-aware-balance-policy
  [[child {:keys [exclusion-threshold min-measure retry-period scale update-rate]}]]
  (let [retry-period (time-period retry-period)
        scale        (time-period scale)
        update-rate  (time-period update-rate)

        builder (LatencyAwarePolicy/builder child)]
    (when exclusion-threshold
      (.withExclusionThreshold ^LatencyAwarePolicy$Builder builder
                               ^double (double exclusion-threshold)))
    (when min-measure
      (.withMininumMeasurements ^LatencyAwarePolicy$Builder builder
                                ^int (int min-measure)))
    (when retry-period
      (.withRetryPeriod ^LatencyAwarePolicy$Builder builder
                        ^long (long (:value retry-period))
                        ^TimeUnit (:unit retry-period)))
    (when  scale
      (.withScale ^LatencyAwarePolicy$Builder builder
                  ^long (long (:value scale))
                  ^TimeUnit (:unit scale)))
    (when update-rate
      (.withUpdateRate ^LatencyAwarePolicy$Builder builder
                       ^long (long (:value update-rate))
                       ^TimeUnit (:unit update-rate)))
    (.build ^LatencyAwarePolicy$Builder builder)))

(declare load-balancing-policy)

(defn white-list-policy
  [{:keys [child white-list]}]
  (lb/whitelist-policy
   (load-balancing-policy child) (map socket-address white-list)))

(defn dc-aware-round-robin-policy
  [{:keys [data-centre used-hosts-per-remote-dc]}]
  (lb/dc-aware-round-robin-policy data-centre
                                  used-hosts-per-remote-dc))

(defn load-balancing-policy
  [policy]
  (case (or (:type policy) policy)
    :default                           (Policies/defaultLoadBalancingPolicy)
    :round-robin                       (lb/round-robin-policy)
    :white-list                        (white-list-policy policy)
    :dc-aware-round-robin              (dc-aware-round-robin-policy policy)
    :token-aware/round-robin          (lb/token-aware-policy
                                       (lb/round-robin-policy))
    :token-aware/white-list           (lb/token-aware-policy
                                       (white-list-policy policy))
    :token-aware/dc-aware-round-robin (lb/token-aware-policy
                                       (dc-aware-round-robin-policy policy))
    :latency-aware/round-robin          (latency-aware-balance-policy
                                         (lb/round-robin-policy)
                                         policy)
    :latency-aware/white-list           (latency-aware-balance-policy
                                         (white-list-policy policy)
                                         policy)
    :latency-aware/dc-aware-round-robin (latency-aware-balance-policy
                                         (dc-aware-round-robin-policy policy)
                                         policy)))

(defmethod set-cluster-option! :load-balancing-policy
  [_ ^Cluster$Builder builder policy]
  (.withLoadBalancingPolicy builder
                            (if (instance? LoadBalancingPolicy policy)
                              policy
                              (load-balancing-policy policy))))

(defn constant-reconnection-policy
  [{:keys [constant-delay-ms]}]
  (reconnection/constant-reconnection-policy constant-delay-ms))

(defn exponential-reconnection-policy
  [{:keys [base-delay-ms max-delay-ms]}]
  (reconnection/exponential-reconnection-policy base-delay-ms max-delay-ms))

(defn reconnection-policy
  [policy]
  (case (or (:type policy) policy)
    :default     (Policies/defaultReconnectionPolicy)
    :constant    (constant-reconnection-policy policy)
    :exponential (exponential-reconnection-policy policy)))

(defmethod set-cluster-option! :reconnection-policy
  [_ ^Cluster$Builder builder policy]
  (.withReconnectionPolicy builder
                           (if (instance? ReconnectionPolicy policy)
                             policy
                             (reconnection-policy policy))))

(defn constant-speculative-execution-policy
  [{:keys [constant-delay-millis max-speculative-executions]}]
  (se/constant-speculative-execution-policy
   constant-delay-millis
   max-speculative-executions))

(defn percentile-tracker
  [^PercentileTracker$Builder builder
   {:keys [interval min-recorded-values significant-value-digits]}]
  (let [interval (time-period interval)]
    (when interval
      (.withInterval ^PercentileTracker$Builder builder
                     ^long (long (:value interval))
                     ^TimeUnit (:unit interval)))
    (when min-recorded-values
      (.withMinRecordedValues ^PercentileTracker$Builder builder
                              ^int (int min-recorded-values)))
    (when significant-value-digits
      (.withNumberOfSignificantValueDigits ^PercentileTracker$Builder builder
                                           ^int (int significant-value-digits)))
    (.build ^PercentileTracker$Builder builder)))

(defn cluster-wide-percentile-tracker
  [{:keys [highest-trackable-latency-millis] :as opts}]
  (percentile-tracker
   (ClusterWidePercentileTracker/builder highest-trackable-latency-millis)
   opts))

(defn per-host-percentile-tracker
  [{:keys [highest-trackable-latency-millis] :as opts}]
  (percentile-tracker
   (PerHostPercentileTracker/builder highest-trackable-latency-millis)
   opts))

(defn percentile-speculative-execution-policy
  [tracker {:keys [percentile max-executions]}]
  (se/percentile-speculative-execution-policy tracker
                                              percentile
                                              max-executions))

(defn speculative-execution-policy
  [policy]
  (case (or (:type policy) policy)
    :default                         (Policies/defaultSpeculativeExecutionPolicy)
    :none                            (se/no-speculative-execution-policy)
    :constant                        (constant-speculative-execution-policy policy)
    :cluster-wide-percentile-tracker (percentile-speculative-execution-policy
                                      (cluster-wide-percentile-tracker policy)
                                      policy)
    :per-host-percentile-tracker     (percentile-speculative-execution-policy
                                      (per-host-percentile-tracker policy)
                                      policy)))

(defmethod set-cluster-option! :speculative-execution-policy
  [_ ^Cluster$Builder builder policy]
  (.withSpeculativeExecutionPolicy builder
                                   (if (instance? SpeculativeExecutionPolicy policy)
                                     policy
                                     (speculative-execution-policy policy))))

(defmethod set-cluster-option! :pooling-options
  [_ ^Cluster$Builder builder {:keys [core-connections-per-host
                                      max-connections-per-host
                                      connection-thresholds]
                               :as pooling-options}]
  ;; (doseq [[opt x] pooling-options]
  ;;   (set-cluster-option! opt builder x))
  (let [pooling-options (PoolingOptions.)]
    (when core-connections-per-host
      (doseq [[dist value] core-connections-per-host]
        (.setCoreConnectionsPerHost pooling-options
                                    (enum/host-distance dist)
                                    (int value))))
    (when max-connections-per-host
      (doseq [[dist value] max-connections-per-host]
        (.setMaxConnectionsPerHost pooling-options
                                   (enum/host-distance dist)
                                   (int value))))
    (when connection-thresholds
      (doseq [[dist value] connection-thresholds]
        (.setNewConnectionThreshold pooling-options
                                    (enum/host-distance dist)
                                    (int value))))
    (.withPoolingOptions builder pooling-options)))

(defmethod set-cluster-option! :socket-options
  [_ ^Cluster$Builder builder {:keys [connect-timeout
                                      read-timeout
                                      receive-buffer-size
                                      send-buffer-size
                                      so-linger
                                      tcp-no-delay?
                                      reuse-address?
                                      keep-alive?
                                      ;; bc
                                      connect-timeout-millis
                                      read-timeout-millis]
                               :as socket-options}]
  (let [socket-options (SocketOptions.)]
    (some->> connect-timeout int (.setConnectTimeoutMillis socket-options))
    (some->> read-timeout int (.setReadTimeoutMillis socket-options))
    (some->> connect-timeout-millis int (.setConnectTimeoutMillis socket-options))
    (some->> read-timeout-millis int (.setReadTimeoutMillis socket-options))
    (some->> receive-buffer-size int (.setReceiveBufferSize socket-options))
    (some->> send-buffer-size int (.setSendBufferSize socket-options))
    (some->> so-linger int (.setSoLinger socket-options))
    (some->> tcp-no-delay? boolean (.setTcpNoDelay socket-options))
    (some->> reuse-address? boolean (.setReuseAddress socket-options))
    (some->> keep-alive? boolean (.setKeepAlive socket-options))
    (.withSocketOptions builder socket-options)))

(defmethod set-cluster-option! :query-options
  [_ ^Cluster$Builder builder {:keys [fetch-size
                                      consistency
                                      serial-consistency]}]
  (let [query-options (QueryOptions.)]
    (some->> fetch-size int (.setFetchSize query-options))
    (some->> consistency enum/consistency-level (.setConsistencyLevel query-options))
    (some->> serial-consistency enum/consistency-level (.setSerialConsistencyLevel query-options))
    (.withQueryOptions builder query-options)))

(defmethod set-cluster-option! :metrics?
  [_ ^Cluster$Builder builder metrics?]
  (when (not metrics?)
    (.withoutMetrics builder))
  builder)

(defmethod set-cluster-option! :jmx-reporting?
  [_ ^Cluster$Builder builder jmx-reporting?]
  (when (not jmx-reporting?)
    (.withoutJMXReporting builder))
  builder)

(defmethod set-cluster-option! :credentials
  [_ ^Cluster$Builder builder {:keys [user password]}]
  (.withCredentials builder user password))

(defmethod set-cluster-option! :auth-provider
  [_ ^Cluster$Builder builder auth-provider]
  (.withAuthProvider builder auth-provider))

(defmethod set-cluster-option! :kerberos?
  [_ ^Cluster$Builder builder kerberos?]
  (when kerberos?
    (.withAuthProvider builder (DseGSSAPIAuthProvider.)))
  builder)

(defmethod set-cluster-option! :compression
  [_ ^Cluster$Builder builder option]
  (.withCompression builder (enum/compression option)))

(defmethod set-cluster-option! :ssl?
  [_ ^Cluster$Builder builder ssl?]
  (when ssl? (.withSSL builder))
  builder)

(defmethod set-cluster-option! :ssl-options
  [_ ^Cluster$Builder builder ssl-options]
  (.withSSL builder
            (if (instance? SSLOptions ssl-options)
              ssl-options
              (let [{:keys [keystore-path keystore-password cipher-suites]} ssl-options
                    keystore (KeyStore/getInstance "JKS")
                    ssl-context (SSLContext/getInstance "SSL")
                    keymanager (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm))
                    trustmanager (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
                    password (char-array keystore-password)]
                (.load keystore (io/input-stream keystore-path) password)
                (.init keymanager keystore password)
                (.init trustmanager keystore)
                (.init ssl-context
                       (.getKeyManagers keymanager)
                       (.getTrustManagers trustmanager) nil)
                (.build (doto (JdkSSLOptions/builder)
                          (.withCipherSuites ^Builder
                                             (into-array String
                                                         (if cipher-suites
                                                           cipher-suites
                                                           ["TLS_RSA_WITH_AES_128_CBC_SHA"
                                                            "TLS_RSA_WITH_AES_256_CBC_SHA"])))
                          (.withSSLContext ssl-context)))))))

(defmethod set-cluster-option! :timestamp-generator
  [_ ^Cluster$Builder builder ts-generator]
  (.withTimestampGenerator builder
                           (if (instance? TimestampGenerator ts-generator)
                             ts-generator
                             (case ts-generator
                               :atomic-monotonic (tsg/atomic-monotonic)
                               :server-side (tsg/server-side)
                               :thread-local (tsg/thread-local)))))

(defn address-translator
  [at]
  (case at
    :identity         (at/identity-translator)
    :ec2-multi-region (at/ec2-multi-region-address-translator)))

(defmethod set-cluster-option! :address-translator
  [_ ^Cluster$Builder builder at]
  (.withAddressTranslator builder
                          (if (instance? AddressTranslator at)
                            at
                            (address-translator at))))

(defmethod set-cluster-option! :netty-options
  [_ ^Cluster$Builder builder netty-options]
  (.withNettyOptions builder netty-options))

(defmethod set-cluster-option! :max-schema-agreement-wait-seconds
  [_ ^Cluster$Builder builder max-schema-agreement-wait-seconds]
  (.withMaxSchemaAgreementWaitSeconds builder
                                      (int max-schema-agreement-wait-seconds))
  builder)

(defmethod set-cluster-option! :cluster-name
  [_ ^Cluster$Builder builder cluster-name]
  (.withClusterName builder (name cluster-name)))

(defn set-cluster-options!
  ^Cluster$Builder
  [^Cluster$Builder builder options]
  (reduce (fn [builder [k option]]
            (set-cluster-option! k builder option))
          builder
          options))
