(load-file "../../.deps-versions.clj")
(defproject cc.qbits/alia-embedded alia-version
  :description "Embedded Cassandra useful for Alia tests"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure ~clj-version]
                 [exoscale/clj-yaml "0.5.6"]
                 [cc.qbits/alia ~alia-version]
                 [org.apache.cassandra/cassandra-all "3.11.1"]]
  :global-vars {*warn-on-reflection* true})
