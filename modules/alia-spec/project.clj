(defproject cc.qbits/alia-spec "4.0.0-SNAPSHOT"
  :description "clojure.spec for Alia"
  :url "https://github.com/mpenet/alia/alia-joda-time"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]
                 [org.clojure/test.check "0.9.0"]
                 [cc.qbits/spex "0.1.1"]
                 [cc.qbits/alia "3.2.0" :exclude [org.clojure/clojure]]]
  :global-vars {*warn-on-reflection* true})
