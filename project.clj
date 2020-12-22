(defproject cc.qbits/alia-all "5.0.0"
  :description "Cassandra CQL3 client for Clojure - datastax/java-driver wrapper"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[lein-sub "0.3.0"]]

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]

  :managed-dependencies [[org.clojure/clojure "1.10.1"]
                         [cc.qbits/alia "5.0.0"]
                         [cc.qbits/alia-manifold "5.0.0"]
                         [cc.qbits/alia-async "5.0.0"]
                         [cc.qbits/alia-joda-time "5.0.0"]
                         [cc.qbits/alia-java-legacy-time "5.0.0"]
                         [cc.qbits/alia-spec "5.0.0"]
                         [cc.qbits/alia-component "5.0.0"]]

  :dependencies [[org.clojure/clojure]
                 [cc.qbits/alia]
                 [cc.qbits/alia-manifold]
                 [cc.qbits/alia-async]
                 [cc.qbits/alia-joda-time]
                 [cc.qbits/alia-java-legacy-time]
                 [cc.qbits/alia-spec]
                 [cc.qbits/alia-component]]

  :sub ["modules/alia"
        "modules/alia-async"
        "modules/alia-java-legacy-time"
        "modules/alia-joda-time"
        "modules/alia-manifold"
        "modules/alia-spec"
        "modules/alia-component"]

  :profiles {:dev
             {:plugins [[codox "0.10.3"]]
              :dependencies [[org.xerial.snappy/snappy-java "1.0.5"]
                             [cc.qbits/hayt "4.1.0"
                              :exclusions [org.apache.commons/commons-lang3]]
                             [net.jpountz.lz4/lz4 "1.3.0"]
                             [clj-time "0.11.0"]
                             [manifold "0.1.8"]
                             [org.clojure/tools.logging "0.3.1"]
                             [org.slf4j/slf4j-log4j12 "1.7.25"
                              :exclusions [org.slf4j/slf4j-api]]]}}
  :jar-exclusions [#"log4j.properties"]
  :monkeypatch-clojure-test false
  :codox {:source-uri "https://github.com/mpenet/alia/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :source-paths ["modules/alia/src"
                         "modules/alia-manifold/src"
                         "modules/alia-async/src"
                         "modules/alia-joda-time/src"
                         "modules/alia-java-legacy-time/src"
                         "modules/alia-spec/src"
                         "modules/alia-component/src"]}

  :global-vars {*warn-on-reflection* true}
  :pedantic? :abort

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["sub" "install"]
                  ["install"]
                  ["sub" "deploy" "clojars"]
                  ["deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
