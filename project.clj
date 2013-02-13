(defproject cc.qbits/alia "0.1.0-SNAPSHOT"
  :description "Cassandra CQL3 client for Clojure (datastax/java-driver wrapper)"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [cc.qbits/knit "0.2.1"]
                 [com.datastax.cassandra/cassandra-driver-core "0.1.0-SNAPSHOT"]
                 [clj-time "0.4.4"]]
  :warn-on-reflection true)
