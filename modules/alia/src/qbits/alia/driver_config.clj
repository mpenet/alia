(ns qbits.alia.driver-config
  (:require
   [qbits.alia.enum :as enum])
  (:import
   [com.datastax.oss.driver.api.core.config
    DriverOption
    DriverConfigLoader
    ProgrammaticDriverConfigLoaderBuilder]))

(defn programmatic-driver-config-loader
  []
  (DriverConfigLoader/programmaticBuilder))

(defprotocol IDriverConfigLoaderBuilder
  (-with-config [v builder driver-option]))

(extend-protocol IDriverConfigLoaderBuilder
  Boolean
  (-with-config [b
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (.withBoolean builder driver-option b))

  Integer
  (-with-config [i
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (.withInt builder driver-option i))

  Long
  (-with-config [l
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (.withLong builder driver-option l))

  Double
  (-with-config [d
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (.withDouble builder driver-option d))

  String
  (-with-config [s
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (.withString builder driver-option s))

  Class
  (-with-config [c
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (.withClass builder driver-option c)))

(defn with-config
  [^ProgrammaticDriverConfigLoaderBuilder builder
   driver-option
   v]
  (-with-config v builder (enum/driver-option driver-option)))

(defn make-config-loader
  "given a clojure map of {driver-option-key option-value} calls
   the appropriate with* methods on a ProgrammaticDriverConfigLoaderBuilder
   and builds a DriverConfigLoader"
  [config]
  (let [^ProgrammaticDriverConfigLoaderBuilder
        builder (programmatic-driver-config-loader)

        ^ProgrammaticDriverConfigLoaderBuilder
        builder (reduce
                 (fn [^ProgrammaticDriverConfigLoaderBuilder builder
                     [k v]]
                   (if (= ::without v)
                     (.without builder (enum/driver-option k))
                     (with-config builder k v)))
                 builder
                 config)]
    (.build builder)))
