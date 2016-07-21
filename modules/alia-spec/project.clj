(defproject cc.qbits/alia-spec "3.1.9"
  :description "clojure.spec for Alia"
  :url "https://github.com/mpenet/alia/alia-joda-time"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                 [org.clojure/test.check "0.9.0"]
                 [cc.qbits/alia "3.1.9" :exclude [org.clojure/clojure]]]
  :global-vars {*warn-on-reflection* true})
