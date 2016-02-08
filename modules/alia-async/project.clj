(defproject cc.qbits/alia-async "3.1.0"
  :description "core.async interface for Alia"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cc.qbits/alia "3.1.0"]
                 [org.clojure/core.async "0.2.374"]]
  :global-vars {*warn-on-reflection* true})
