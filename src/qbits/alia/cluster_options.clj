(ns qbits.alia.cluster-options
  (:require [qbits.alia.enum :as enum])
  (:import
   (com.datastax.driver.core
    Cluster$Builder
    HostDistance
    PoolingOptions
    ProtocolOptions$Compression
    QueryOptions
    SocketOptions
    SSLOptions)
   (com.datastax.driver.core.policies
    LoadBalancingPolicy
    ReconnectionPolicy
    RetryPolicy)))

(defmulti set-cluster-option! (fn [k ^Cluster$Builder builder option] k))

(defmethod set-cluster-option! :contact-points
  [_ builder hosts]
  (.addContactPoints ^Cluster$Builder builder
                     ^"[Ljava.lang.String;"
                     (into-array (if (sequential? hosts) hosts [hosts]))))

(defmethod set-cluster-option! :port
  [_ builder port]
  (.withPort ^Cluster$Builder builder (int port)))

(defmethod set-cluster-option! :load-balancing-policy
  [_ ^Cluster$Builder builder ^LoadBalancingPolicy policy]
  (.withLoadBalancingPolicy builder policy))

(defmethod set-cluster-option! :reconnection-policy
  [_ ^Cluster$Builder builder ^ReconnectionPolicy policy]
  (.withReconnectionPolicy builder policy))

(defmethod set-cluster-option! :retry-policy
  [_ ^Cluster$Builder builder ^RetryPolicy policy]
  (.withRetryPolicy builder policy))

(defmethod set-cluster-option! :pooling-options
  [_ ^Cluster$Builder builder {:keys [core-connections-per-host
                                      max-connections-per-host
                                      max-simultaneous-requests-per-connection
                                      min-simultaneous-requests-per-connection]}]
  (let [^PoolingOptions po (PoolingOptions.)]
    (doseq [[dist value] core-connections-per-host]
      (.setCoreConnectionsPerHost po (enum/host-distance dist) (int value)))
    (doseq [[dist value] max-connections-per-host]
      (.setMaxConnectionsPerHost po (enum/host-distance dist) (int value)))
    (doseq [[dist value] max-simultaneous-requests-per-connection]
      (.setMaxSimultaneousRequestsPerConnectionThreshold po
                                                         (enum/host-distance dist)
                                                         (int value)))
    (doseq [[dist value] min-simultaneous-requests-per-connection]
      (.setMinSimultaneousRequestsPerConnectionThreshold po
                                                         (enum/host-distance dist)
                                                         (int value)))
    (.withPoolingOptions builder po))
  builder)

(defmethod set-cluster-option! :socket-options
  [_ ^Cluster$Builder builder {:keys [connect-timeout-millis
                                      read-timeout-millis
                                      receive-buffer-size
                                      send-buffer-size
                                      so-linger
                                      tcp-no-delay?
                                      reuse-address?
                                      keep-alive?]}]
  (let [so (SocketOptions.)]
    (when connect-timeout-millis
      (.setConnectTimeoutMillis so (int connect-timeout-millis)))
    (when read-timeout-millis
      (.setReadTimeoutMillis so (int read-timeout-millis)))
    (when receive-buffer-size
      (.setReceiveBufferSize so (int receive-buffer-size)))
    (when send-buffer-size
      (.setSendBufferSize so (int send-buffer-size)))
    (when so-linger
      (.setSoLinger so (int so-linger)))
    (when tcp-no-delay?
      (.setTcpNoDelay so (boolean tcp-no-delay?)))
    (when reuse-address?
      (.setReuseAddress so (boolean reuse-address?)))
    (when keep-alive?
      (.setKeepAlive so (boolean keep-alive?)))

    (.withSocketOptions builder so)))


(defmethod set-cluster-option! :query-options
  [_ ^Cluster$Builder builder {:keys [fetch-size
                                      consistency
                                      serial-consistency]}]
  (let [qo (QueryOptions.)]
    (when fetch-size
      (.setFetchSize qo (int fetch-size)))
    (when consistency
      (.setConsistencyLevel qo (enum/consistency-levels consistency)))
    (when serial-consistency
      (.setSerialConsistencyLevel qo (enum/consistency-levels serial-consistency)))
    (.withQueryOptions builder qo)))

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

(defmethod set-cluster-option! :compression
  [_ ^Cluster$Builder builder option]
  (.withCompression builder (enum/compression option)))

(defmethod set-cluster-option! :ssl?
  [_ ^Cluster$Builder builder ssl?]
  (when ssl? (.withSSL builder)))

(defmethod set-cluster-option! :ssl-options
  [_ ^Cluster$Builder builder ssl-options]
  (assert (instance? ssl-options SSLOptions)
          "Expects a com.datastax.driver.core.SSLOptions instance")
  (.withSSL builder ssl-options))

(defn set-cluster-options!
  ^Cluster$Builder
  [^Cluster$Builder builder options]
  (reduce (fn [builder [k option]]
            (set-cluster-option! k builder option))
          builder
          options))
