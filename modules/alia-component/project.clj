(defproject cc.qbits/alia-component "_"
  :plugins [[lein-parent "0.3.8"]
            [exoscale/lein-replace "0.1.1"]]

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

  :exclusions [org.clojure/clojure]

  :description "Component integration for Alia"

  :dependencies [[com.stuartsierra/component "0.3.2"]
                 [cc.qbits/alia]
                 [cc.qbits/alia-spec]])
