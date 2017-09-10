(load-file "../../.deps-versions.clj")
(defproject cc.qbits/alia-manifold alia-version
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure ~clj-version]
                 [cc.qbits/alia ~alia-version]
                 [manifold "0.1.6"]]
  :global-vars {*warn-on-reflection* true})
