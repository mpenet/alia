(ns qbits.alia.session-builder
  (:require
   [qbits.alia.enum :as enum]
   [qbits.alia.driver-config :as driver-config])
  (:import
   [com.datastax.oss.driver.api.core
    CqlSession
    CqlSessionBuilder]
   [com.datastax.oss.driver.api.core.config
    DriverConfigLoader]))


(defn cql-session-builder
  [config]
  (let [^DriverConfigLoader
        config-loader (driver-config/make-config-loader config)]

    (-> (CqlSession/builder)
        (.withConfigLoader config-loader))))
