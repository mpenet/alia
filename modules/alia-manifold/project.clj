(defproject cc.qbits/alia-manifold "_"
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

  :description "manifold interface for Alia"

  :dependencies [[org.clojure/clojure]
                 [cc.qbits/alia]
                 [manifold "0.1.8"]])
