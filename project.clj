(defproject cc.qbits/alia "3.0.0"
  :description "Cassandra CQL3 client for Clojure - datastax/java-driver wrapper"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cc.qbits/commons "0.4.5"]
                 [com.datastax.cassandra/cassandra-driver-core "3.0.0"
                  :classifier "shaded"
                  :exclusions [io.netty/*]]
                 [com.datastax.cassandra/cassandra-driver-dse "3.0.0-rc1"
                  :exclusions [com.datastax.cassandra/cassandra-driver-core]]
                 [org.clojure/core.async "0.2.374"]]
  :profiles {:dev  {:dependencies [[org.xerial.snappy/snappy-java "1.0.5"]
                                   [cc.qbits/hayt "3.0.1"]
                                   [net.jpountz.lz4/lz4 "1.2.0"]
                                   [clj-time "0.8.0"]
                                   [com.taoensso/nippy "2.9.0"]
                                   [cc.qbits/tardis "1.0.0"]
                                   [codox "0.9.1"]
                                   [manifold "0.1.1"]
                                   [org.slf4j/slf4j-log4j12 "1.7.3"]]}}
  :jar-exclusions [#"log4j.properties"]
  :codox {:source-uri "https://github.com/mpenet/alia/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :namespaces :all}
  :global-vars {*warn-on-reflection* true})
