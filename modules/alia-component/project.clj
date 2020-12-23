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

  :description "Component integration for Alia"

  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [com.stuartsierra/component "0.3.2"]
                 [cc.qbits/alia :version]
                 [cc.qbits/alia-spec :version]])
