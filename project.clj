(defproject cc.qbits/alia "1.2.0-SNAPSHOT"
  :description "Cassandra CQL3 client for Clojure - datastax/java-driver wrapper"
  :url "https://github.com/mpenet/alia"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.memoize "0.5.3"]
                 [cc.qbits/knit "0.2.1"]
                 [cc.qbits/hayt "1.0.2"]
                 [lamina "0.5.0-rc2"]
                 [com.datastax.cassandra/cassandra-driver-core "1.0.0"]]
  :profiles {:1.4  {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5  {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6  {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}
             :dev  {:dependencies [[org.xerial.snappy/snappy-java "1.0.5"]
                                   [clj-time "0.5.0"]
                                   [cc.qbits/tardis "1.0.0"]
                                   [commons-lang/commons-lang "2.6"]]}
             :test  {:resource-paths ["test/resources"]}}
  :codox {:src-dir-uri "https://github.com/mpenet/alia/blob/master"
          :src-linenum-anchor-prefix "L"}
  :warn-on-reflection true
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}})
