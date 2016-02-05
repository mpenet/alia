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
                  :exclusions [com.datastax.cassandra/cassandra-driver-core]]]
  :jar-exclusions [#"log4j.properties"]
  :codox {:source-uri "https://github.com/mpenet/alia/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :namespaces :all}
  :aot :all
  :global-vars {*warn-on-reflection* true})
