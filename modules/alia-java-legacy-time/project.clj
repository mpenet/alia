(defproject cc.qbits/alia-java-legacy-time "_"
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

  :description "Alia extension for java legacy time codec"

  :dependencies [[org.clojure/clojure]
                 [cc.qbits/alia]])
