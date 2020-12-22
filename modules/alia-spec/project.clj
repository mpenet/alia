(defproject cc.qbits/alia-spec "_"
  :plugins [[lein-parent "0.3.8"]]

  :parent-project {:path "../../project.clj"
                   :inherit [:version
                             :managed-dependencies
                             :license
                             :url
                             :scm
                             :deploy-repositories
                             :profiles
                             :pedantic?
                             :jar-exclusions
                             :global-vars]}

  :description "clojure.spec for Alia"

  :dependencies [[org.clojure/clojure]
                 [cc.qbits/alia]
                 [org.clojure/test.check "1.1.0"]])
