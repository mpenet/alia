(ns qbits.alia.test.embedded
  (:require [clojure.java.io :as io])
  (:use [clojure.test])
  (:import
   (org.apache.cassandra.service EmbeddedCassandraService)))

(System/setProperty "cassandra.config" (str (io/resource "cassandra.yaml")))
(System/setProperty "cassandra-foreground" "yes")
(System/setProperty "log4j.defaultInitOverride" "false")

(defn start-service!
  []
  (doto (EmbeddedCassandraService.)
    (.start)))

(defonce service (start-service!))
