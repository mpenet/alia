(defproject cc.qbits/alia alia-version
  :description "Cassandra CQL3 client for Clojure - datastax/java-driver wrapper"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure ~clj-version]
                 [cc.qbits/commons "0.4.6"]
                 [com.datastax.cassandra/cassandra-driver-core "3.1.4"
                  :classifier "shaded"
                  :exclusions [io.netty/*]]
                 [com.datastax.cassandra/dse-driver "1.1.0"
                  :exclusions [com.datastax.cassandra/cassandra-driver-core]]]
  :jar-exclusions [#"log4j.properties"]
  :codox {:source-uri "https://github.com/mpenet/alia/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :namespaces :all}
  :jvm-opts ^:replace ["-server"]
  :global-vars {*warn-on-reflection* true})
