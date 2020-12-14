(load-file "../../.deps-versions.clj")
(defproject cc.qbits/alia-java-legacy-time alia-version
  :description "Alia extension for java legacy time codec"
  :url "https://github.com/mpenet/alia/alia-java-legacy-time"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure ~clj-version]
                 [cc.qbits/alia ~alia-version]]
  :global-vars {*warn-on-reflection* true})
