(ns qbits.alia.cluster-options
  (:require [qbits.alia.enum :as enum])
  (:import
   (com.datastax.driver.core
    Cluster$Builder
    HostDistance
    PoolingOptions
    ProtocolOptions$Compression
    SocketOptions)
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
  [_ ^Cluster$Builder builder options]
  (let [^PoolingOptions po (.poolingOptions builder)]
    (doseq [[dist value] (:core-connections-per-host options)]
      (.setCoreConnectionsPerHost po (enum/host-distance dist) (int value)))
    (doseq [[dist value] (:max-connections-per-host options)]
      (.setMaxConnectionsPerHost po (enum/host-distance dist) (int value)))
    (doseq [[dist value] (:max-simultaneous-requests-per-connection options)]
      (.setMaxSimultaneousRequestsPerConnectionTreshold po
                                                        (enum/host-distance dist)
                                                        (int value)))
    (doseq [[dist value] (:min-simultaneous-requests-per-connection options)]
      (.setMinSimultaneousRequestsPerConnectionTreshold po
                                                        (enum/host-distance dist)
                                                        (int value))))
  builder)

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

(defn set-cluster-options!
  ^Cluster$Builder
  [^Cluster$Builder builder options]
  (reduce (fn [builder [k option]]
            (set-cluster-option! k builder option))
          builder
          options))
