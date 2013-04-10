(ns qbits.alia.test.embedded
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell])
  (:use [clojure.test])
  (:import
   (org.apache.cassandra.service EmbeddedCassandraService)))

(System/setProperty "cassandra.config" (str (io/resource "cassandra.yaml")))
(System/setProperty "cassandra-foreground" "yes")
(System/setProperty "log4j.defaultInitOverride" "false")

(defn start-service!
  []
  ;; cleanup previous runs data
  (println "Clear previous run data")
  (shell/sh "rm" "tmp -rf")
  (println "Starting EmbeddedCassandraService")
  (let [s (EmbeddedCassandraService.)]
    (.start s)
    (println "Service started")
    s))

(defonce service (start-service!))
