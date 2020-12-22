(defproject cc.qbits/alia "_"

  :plugins [[lein-parent "0.3.8"]]

  :parent-project {:path "../../project.clj"
                   :inherit [:version
                             :managed-dependencies
                             :license
                             :url
                             :scm
                             :deploy-repositories
                             :profiles
                             :description
                             :pedantic?
                             :jar-exclusions
                             :global-vars]}

  :dependencies [[org.clojure/clojure]
                 [cc.qbits/commons "0.5.2"]
                 [com.datastax.oss/java-driver-core-shaded "4.9.0"]]

  :codox {:source-uri "https://github.com/mpenet/alia/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :namespaces :all}

  :jvm-opts ^:replace ["-server"])
