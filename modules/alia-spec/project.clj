(load-file "../../.deps-versions.clj")
(defproject cc.qbits/alia-spec alia-version
  :description "clojure.spec for Alia"
  :url "https://github.com/mpenet/alia/alia-joda-time"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure ~clj-version]
                 [org.clojure/test.check "0.9.0"]
                 [cc.qbits/alia ~alia-version]]
  :global-vars {*warn-on-reflection* true})
