(defproject cc.qbits/alia "0.1.0-SNAPSHOT"
  :description "Cassandra CQL3 client for Clojure (datastax/java-driver wrapper)"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [cc.qbits/knit "0.2.1"]
                 [cc.qbits/tardis "1.0.0"]
                 [cc.qbits/hayt "0.1.0-SNAPSHOT"]
                 [com.datastax.cassandra/cassandra-driver-core "1.0.0-beta1"]
                 [clj-time "0.4.4"]]
  :profiles {:1.4  {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5  {:dependencies [[org.clojure/clojure "1.5.0-master-SNAPSHOT"]]}
             :test {:dependencies []}}
  :codox {:src-dir-uri "https://github.com/mpenet/alia/blob/master"
          :src-linenum-anchor-prefix "L"}
  :warn-on-reflection true)
