(defproject cc.qbits/alia "2.0.0-beta7"
  :description "Cassandra CQL3 client for Clojure - datastax/java-driver wrapper"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.memoize "0.5.6"]
                 [cc.qbits/knit "0.2.1"]
                 [cc.qbits/hayt "2.0.0-beta2"]
                 [lamina "0.5.2"]
                 [com.datastax.cassandra/cassandra-driver-core "2.0.0-rc2"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]]
  :profiles {:1.4  {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5  {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6  {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}
             :dev  {:dependencies [[org.xerial.snappy/snappy-java "1.0.5"]
                                   [clj-time "0.6.0"]
                                   [cc.qbits/tardis "1.0.0"]]
                    :jvm-opts     ["-javaagent:lib/jamm-0.2.5.jar"]}
             :test  {:resource-paths ["test/resources"]
                     :dependencies [[org.apache.cassandra/cassandra-all "2.0.2"]]
                     :jvm-opts     ["-javaagent:lib/jamm-0.2.5.jar"]}}
:codox {:src-dir-uri "https://github.com/mpenet/alia/blob/master"
        :src-linenum-anchor-prefix "L"
        :exclude [qbits.alia.enum
                  qbits.alia.utils
                  qbits.alia.codec
                  qbits.alia.cluster-options]}
:global-vars {*warn-on-reflection* true})
