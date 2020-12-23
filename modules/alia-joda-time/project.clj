(defproject cc.qbits/alia-joda-time "_"
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

  :description "Alia extension for joda-time codec"

  :dependencies [[org.clojure/clojure]
                 [cc.qbits/alia]
                 [clj-time "0.11.0"]])
