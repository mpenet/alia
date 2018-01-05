(load-file ".deps-versions.clj")
(defproject cc.qbits/alia-all alia-version
  :description "Cassandra CQL3 client for Clojure - datastax/java-driver wrapper"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure ~clj-version]
                 [cc.qbits/alia ~alia-version]
                 [cc.qbits/alia-manifold ~alia-version]
                 [cc.qbits/alia-async ~alia-version]
                 [cc.qbits/alia-joda-time ~alia-version]
                 [cc.qbits/alia-spec ~alia-version]]
  :profiles {:dev
             {:plugins [[codox "0.10.2"]]
              :dependencies [[org.xerial.snappy/snappy-java "1.0.5"]
                             [cc.qbits/hayt "4.0.0-beta6"]
                             [net.jpountz.lz4/lz4 "1.3.0"]
                             [clj-time "0.11.0"]
                             [manifold "0.1.6"]
                             [org.clojure/tools.logging "0.3.1"]
                             [org.slf4j/slf4j-log4j12 "1.7.25"]]}}
  :jar-exclusions [#"log4j.properties"]
  :monkeypatch-clojure-test false
  :codox {:source-uri "https://github.com/mpenet/alia/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :source-paths ["modules/alia/src"
                         "modules/alia-manifold/src"
                         "modules/alia-async/src"
                         "modules/alia-joda-time/src"
                         "modules/alia-eaio-uuid/src"
                         "modules/alia-nippy/src"]}
  :global-vars {*warn-on-reflection* true}
  :pedantic? :abort)
