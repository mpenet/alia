(defproject cc.qbits/alia-async alia-version
  :description "core.async interface for Alia"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure ~clj-version]
                 [cc.qbits/alia ~alia-version]
                 [org.clojure/core.async "0.3.441"]]
  :global-vars {*warn-on-reflection* true})
