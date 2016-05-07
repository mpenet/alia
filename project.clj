(defproject cc.qbits/alia-all "3.1.6"
  :description "Cassandra CQL3 client for Clojure - datastax/java-driver wrapper"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cc.qbits/alia "3.1.6"]
                 [cc.qbits/alia-manifold "3.1.3"]
                 [cc.qbits/alia-async "3.1.3"]
                 [cc.qbits/alia-joda-time "3.1.3"]
                 [cc.qbits/alia-nippy "3.1.4"]
                 [cc.qbits/alia-eaio-uuid "3.1.3"]]
  :profiles {:dev  {:dependencies [[org.xerial.snappy/snappy-java "1.0.5"]
                                   [cc.qbits/hayt "3.0.1"]
                                   [net.jpountz.lz4/lz4 "1.2.0"]
                                   [clj-time "0.11.0"]
                                   [com.taoensso/nippy "2.9.0"]
                                   [cc.qbits/tardis "1.0.0"]
                                   [codox "0.9.1"]
                                   [manifold "0.1.2"]
                                   [org.slf4j/slf4j-log4j12 "1.7.3"]]}}
  :sub ["modules/alia"
        "modules/alia-manifold"
        "modules/alia-async"
        "modules/alia-joda-time"
        "modules/alia-nippy"
        "modules/alia-eaio-uuid"]
  :jar-exclusions [#"log4j.properties"]
  :codox {:source-uri "https://github.com/mpenet/alia/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :source-paths ["modules/alia/src/"
                         "modules/alia-manifold/src"
                         "modules/alia-async/src"
                         "modules/alia-joda-time/src"
                         "modules/alia-eaio-uuid/src"
                         "modules/alia-nippy/src"]}
  :global-vars {*warn-on-reflection* true})
