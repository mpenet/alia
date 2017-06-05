(def alia-version "4.0.0-beta11")
(def clj-version "1.9.0-alpha17")
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
                 [cc.qbits/alia-nippy ~alia-version]
                 [cc.qbits/alia-eaio-uuid ~alia-version]
                 [cc.qbits/alia-spec ~alia-version]]
  :profiles {:dev
             {:plugins [[codox "0.10.2"]
                        [lein-modules "0.3.11"]]
              :dependencies [[org.xerial.snappy/snappy-java "1.0.5"]
                             [cc.qbits/hayt "4.0.0-beta6"]
                             [net.jpountz.lz4/lz4 "1.2.0"]
                             [clj-time "0.11.0"]
                             [com.taoensso/nippy "2.9.0"]
                             [cc.qbits/tardis "1.0.0"]
                             [manifold "0.1.5"]
                             [org.clojure/tools.logging "0.2.6"]
                             [org.slf4j/slf4j-log4j12 "1.7.3"]]}}
  :modules {:dirs ["modules/alia"
                   "modules/alia-manifold"
                   "modules/alia-async"
                   "modules/alia-joda-time"
                   "modules/alia-nippy"
                   "modules/alia-eaio-uuid"
                   "modules/alia-spec"
                   "."]
            :subprocess nil}

  :jar-exclusions [#"log4j.properties"]
  :monkeypatch-clojure-test false
  :codox {:source-uri "https://github.com/mpenet/alia/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :source-paths ["modules/alia/src/"
                         "modules/alia-manifold/src"
                         "modules/alia-async/src"
                         "modules/alia-joda-time/src"
                         "modules/alia-eaio-uuid/src"
                         "modules/alia-nippy/src"]}
  :global-vars {*warn-on-reflection* true})
