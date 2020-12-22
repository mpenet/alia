(defproject cc.qbits/alia-async "_"
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

  :description "core.async interface for Alia"

  :dependencies [[org.clojure/clojure]
                 [cc.qbits/alia]
                 [org.clojure/core.async "0.4.474"]])
