(load-file "../../.deps-versions.clj")
(defproject cc.qbits/alia-component alia-version
  :description "Component integration for Alia"
  :url "https://github.com/mpenet/alia/alia-component"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure ~clj-version]
                 [com.stuartsierra/component "0.3.2"]
                 [cc.qbits/alia ~alia-version]]
  :global-vars {*warn-on-reflection* true})
