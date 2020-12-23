(ns qbits.alia.cql-session
  (:require
   [qbits.alia.enum :as enum]
   [qbits.alia.driver-config :as driver-config])
  (:import
   [com.datastax.oss.driver.api.core
    CqlSession
    CqlSessionBuilder]
   [com.datastax.oss.driver.api.core.type.codec
    TypeCodec]
   [com.datastax.oss.driver.api.core.session
    SessionBuilder]
   [com.datastax.oss.driver.api.core.config
    DriverConfigLoader]
   [com.datastax.oss.driver.api.core.metadata
    EndPoint]
   [com.datastax.oss.driver.api.core.auth
    AuthProvider]
   [com.datastax.oss.driver.api.core.tracker
    RequestTracker]
   [java.net InetSocketAddress]
   [java.util UUID]))


(defn cql-session-builder
  [config]
  (let [^DriverConfigLoader
        config-loader (driver-config/make-config-loader config)]

    (-> (CqlSession/builder)
        (.withConfigLoader config-loader))))

(defn add-contact-point
  [^SessionBuilder session-builder ^InetSocketAddress contact-point]
  (.addContactPoint session-builder contact-point))

(defn add-contact-points
  [^SessionBuilder session-builder contact-points]
  (.addContactPoints session-builder contact-points))

(defn add-type-codecs
  [^SessionBuilder session-builder type-codecs]
  (.addTypeCodecs session-builder (into-array TypeCodec type-codecs)))

(defn with-application-name
  [^SessionBuilder session-builder ^String application-name]
  (.withApplicationName session-builder application-name))

(defn with-application-version
  [^SessionBuilder session-builder ^String application-version]
  (.withApplicationVersion session-builder application-version))

(defn with-auth-credentials
  ([^SessionBuilder session-builder ^String username ^String password]
   (.withAuthCredentials session-builder username password))
  ([^SessionBuilder session-builder ^String username ^String password ^String authorizationId]
   (.withAuthCredentials session-builder username password authorizationId)))

(defn with-auth-provider
  [^SessionBuilder session-builder ^AuthProvider auth-provider]
  (.withAuthProvider session-builder auth-provider))

(defn with-class-loader
  [^SessionBuilder session-builder ^ClassLoader class-loader]
  (.withClassLoader session-builder class-loader))

(defn with-client-id
  [^SessionBuilder session-builder ^UUID client-id]
  (.withClientId session-builder client-id))

(defn with-cloud-proxy-address
  [^SessionBuilder session-builder ^InetSocketAddress cloud-proxy-address]
  (.withCloudProxyAddress session-builder cloud-proxy-address))

(defn with-request-tracker
  [^SessionBuilder session-builder ^RequestTracker request-tracker]
  (.withRequestTracker session-builder request-tracker))
