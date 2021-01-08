(ns qbits.alia.driver-config
  (:require
   [qbits.alia.enum :as enum])
  (:import
   [com.datastax.oss.driver.api.core.config
    DriverOption
    DriverConfigLoader
    ProgrammaticDriverConfigLoaderBuilder]
   [java.util List Map]
   [java.time Duration]))

(defn programmatic-driver-config-loader-builder
  []
  (DriverConfigLoader/programmaticBuilder))

(defprotocol IDriverConfigLoaderBuilder
  (-with-config [v builder driver-option]))

(defn check-element-classes [clname coll]
  (when-not (every? #(= clname (-> % class .getName)) coll)
    (throw (ex-info
            "element-classes must all be the same"
            {:coll coll}))))

;; can't see a way to map .withBytes since the value
;; is a Long, just like .withLong
(extend-protocol IDriverConfigLoaderBuilder
  Duration
  (-with-config [d
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (.withDuration builder driver-option d))

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

  Map
  (-with-config [m
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (.withStringMap builder driver-option m))

  Class
  (-with-config [c
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (.withClass builder driver-option c))

  List
  (-with-config [l
                 ^ProgrammaticDriverConfigLoaderBuilder builder
                 ^DriverOption driver-option]
    (if (> (.size l) 0)
      (let [cl (-> l (.get 0) class .getName)
            _ (check-element-classes cl l)]
        (case cl
          "java.time.Duration" (.withDurationList builder driver-option l)
          "java.lang.Boolean" (.withBooleanList builder driver-option l)
          "java.lang.Integer" (.withIntList builder driver-option l)
          "java.lang.Long" (.withLongList builder driver-option l)
          "jva.lang.Double" (.withDoubleList builder driver-option l)
          "java.lang.String" (.withStringList builder driver-option l)))

      (.withStringList builder driver-option l))))

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
        builder (programmatic-driver-config-loader-builder)

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
