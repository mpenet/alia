(defproject cc.qbits/alia "1.0.0-beta3"
  :description "Cassandra CQL3 client for Clojure (datastax/java-driver wrapper)"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/core.memoize "0.5.3"]
                 [cc.qbits/knit "0.2.1"]
                 [cc.qbits/hayt "0.4.0-beta1"]
                 [cc.qbits/tardis "1.0.0"]
                 [com.datastax.cassandra/cassandra-driver-core "1.0.0-beta1"]]
  :profiles {:1.4  {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5  {:dependencies [[org.clojure/clojure "1.5.0"]]}
             :1.6  {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}
             :dev  {:dependencies [[clj-time "0.4.4"]]}
             :test  {:dependencies [[clj-time "0.4.4"]]}}
  :codox {:src-dir-uri "https://github.com/mpenet/alia/blob/master"
          :src-linenum-anchor-prefix "L"}
  :warn-on-reflection true)
