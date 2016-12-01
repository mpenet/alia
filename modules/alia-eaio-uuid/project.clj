(defproject cc.qbits/alia-eaio-uuid alia-version
  :description "Alia extension for joda-time codec"
  :url "https://github.com/mpenet/alia/alia-joda-time"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure ~clj-version]
                 [cc.qbits/alia ~alia-version]
                 [cc.qbits/tardis "1.0.0"]]
  :global-vars {*warn-on-reflection* true})
