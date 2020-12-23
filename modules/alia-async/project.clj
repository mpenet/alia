(defproject cc.qbits/alia-async "_"
  :plugins [[lein-parent "0.3.8"]]

  :parent-project {:path "../../project.clj"
                   :inherit [:version
                             :managed-dependencies
                             :license
                             :url
                             :deploy-repositories
                             :profiles
                             :pedantic?
                             :jar-exclusions
                             :global-vars]}

  :scm {:name "git"
        :url "https://github.com/mpenet/alia"
        :dir "../.."}

  :description "core.async interface for Alia"

  :dependencies [[org.clojure/clojure]
                 [cc.qbits/alia]
                 [org.clojure/core.async "0.4.474"]])
