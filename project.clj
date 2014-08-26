(defproject cc.qbits/alia "2.1.1-SNAPSHOT"
  :description "Cassandra CQL3 client for Clojure - datastax/java-driver wrapper"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.memoize "0.5.6"
                  :exclusions [org.clojure/core.cache]]
                 [org.clojure/core.cache "0.6.4"]
                 [cc.qbits/knit "0.2.1"]
                 [cc.qbits/hayt "2.0.0-beta4"]
                 [lamina "0.5.4"]
                 [com.datastax.cassandra/cassandra-driver-core "2.1.0"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]]
  :profiles {:1.4  {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5  {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6  {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7  {:dependencies [[org.clojure/clojure "1.7.0-SNAPSHOT"]]}
             :dev  {:dependencies [[org.xerial.snappy/snappy-java "1.0.5"]
                                   [clj-time "0.8.0"]
                                   [cc.qbits/tardis "1.0.0"]
                                   [codox "0.8.10"]]
                    :jvm-opts     ["-javaagent:lib/jamm-0.2.5.jar"]}
             :test  {:resource-paths ["test/resources"]
                     :dependencies [[org.apache.cassandra/cassandra-all "2.1.0-rc6"]]
                     :jvm-opts     ["-javaagent:lib/jamm-0.2.5.jar"]}}
  :codox {:src-dir-uri "https://github.com/mpenet/alia/blob/master/"
          :src-linenum-anchor-prefix "L"
          :defaults {:doc/format :markdown}
          :exclude [qbits.alia.enum
                    qbits.alia.utils
                    qbits.alia.codec
                    qbits.alia.cluster-options]}
  :global-vars {*warn-on-reflection* true})
