(ns qbits.alia.cluster-options
  (:require
   [qbits.alia.enum :as enum]
   [clojure.java.io :as io]
   [qbits.alia.timestamp-generator :as tsg]
   [qbits.alia.policy.address-translator :as at]
   [qbits.alia.policy.load-balancing :as lb]
   [qbits.alia.policy.retry :as retry]
   [qbits.alia.policy.reconnection :as reconnection]
   [qbits.alia.policy.speculative-execution :as se])
  (:import
   (com.datastax.driver.core
    Cluster$Builder
    HostDistance
    PoolingOptions
    ProtocolOptions$Compression
    QueryOptions
    SocketOptions
    JdkSSLOptions
    JdkSSLOptions$Builder
    SSLOptions
    TimestampGenerator)
   (com.datastax.driver.core.policies
    AddressTranslator
    LoadBalancingPolicy
    ReconnectionPolicy
    RetryPolicy
    SpeculativeExecutionPolicy)
   (com.datastax.driver.dse.auth
    ;; DsePlainTextAuthProvider
    DseGSSAPIAuthProvider)
   (javax.net.ssl
    TrustManagerFactory
    KeyManagerFactory
    SSLContext)
   (java.security KeyStore)))

(defmulti set-cluster-option! (fn [k ^Cluster$Builder builder option] k))

(defmethod set-cluster-option! :contact-points
  [_ builder hosts]
  (.addContactPoints ^Cluster$Builder builder
                     ^"[Ljava.lang.String;"
                     (into-array hosts)))

(defmethod set-cluster-option! :port
  [_ builder port]
  (.withPort ^Cluster$Builder builder (int port)))

(defmethod set-cluster-option! :load-balancing-policy
  [_ ^Cluster$Builder builder policy]
  (.withLoadBalancingPolicy builder
                            (if (instance? LoadBalancingPolicy policy)
                              policy
                              (lb/make policy))))

(defmethod set-cluster-option! :reconnection-policy
  [_ ^Cluster$Builder builder policy]
  (.withReconnectionPolicy builder
                           (if (instance? ReconnectionPolicy policy)
                             policy
                             (reconnection/make policy))))

(defmethod set-cluster-option! :retry-policy
  [_ ^Cluster$Builder builder ^RetryPolicy policy]
  (.withRetryPolicy builder (if (instance? RetryPolicy policy)
                              policy
                              (retry/make policy))))

(defmethod set-cluster-option! :speculative-execution-policy
  [_ ^Cluster$Builder builder ^SpeculativeExecutionPolicy policy]
  (.withSpeculativeExecutionPolicy builder
                                   (if (instance? SpeculativeExecutionPolicy policy)
                                     policy
                                     (se/make policy))))

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

(defmethod set-cluster-option! :address-translator
  [_ ^Cluster$Builder builder at]
  (.withAddressTranslator builder
                          (if (instance? AddressTranslator at)
                            at
                            (case at
                              :identity (at/identity-translator)
                              :ec2-multi-region (at/ec2-multi-region-address-translator)))))

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
