(ns qbits.alia.embedded
  (:require [qbits.alia                 :as a]
            [qbits.alia.embedded.config :as cfg]
            [qbits.alia.embedded.jmx    :as jmx])
  (:import java.nio.file.Path
           java.nio.file.Files
           java.nio.file.FileSystem
           java.nio.file.FileVisitor
           java.nio.file.FileVisitResult
           java.nio.file.attribute.FileAttribute
           org.apache.cassandra.service.CassandraDaemon
           org.apache.cassandra.service.StorageService))

(defn shutdown-hook
  [f]
  (-> (Runtime/getRuntime)
      (.addShutdownHook (Thread. ^Runnable f))))

(defn get-port
  "Yield an unused local port"
  []
  (let [sock (java.net.ServerSocket. 0)
        port (.getLocalPort sock)]
    (.close sock)
    port))

(defn delete-path
  [^Path path]
  (-> path .toFile .delete))

(def file-deleter
  (proxy [FileVisitor] []
    (visitFile [file attrs]
      (delete-path file)
      FileVisitResult/CONTINUE)
    (preVisitDirectory [dir attrs]
      FileVisitResult/CONTINUE)
    (visitFileFailed [file e]
      FileVisitResult/CONTINUE)
    (postVisitDirectory [dir e]
      (when e
        (throw e))
      (delete-path dir)
      FileVisitResult/CONTINUE)))

(defn recursive-delete
  [dir]
  (Files/walkFileTree dir file-deleter))

(defn ^Path tmpdir
  [prefix]
  (Files/createTempDirectory prefix (into-array FileAttribute [])))

(defn ^CassandraDaemon activate-daemon
  [dir port]
  (let [daemon       (CassandraDaemon. true)
        storage-port (get-port)
        config-path  (str "file://" dir "/cassandra.yaml")]
    (spit config-path (cfg/yaml-config port storage-port))
    (System/setProperty "cassandra.config"  config-path)
    (System/setProperty "cassandra.native_transport_port" (str port))
    (System/setProperty "cassandra.join_ring" "false")
    (System/setProperty "cassandra.storagedir" (str dir))
    (System/setProperty "cassandra-foreground" "true")
    (.activate daemon)
    daemon))

(defn converge-schema
  [schema port]
  (let [cluster (a/cluster {:contact-points ["localhost"] :port port})
        s       (a/connect cluster)]
    (try
      (doseq [[keyspace statements] schema]
        (a/execute s (str "CREATE KEYSPACE " (name keyspace)
                          " WITH replication = {'class': 'SimpleStrategy',"
                          " 'replication_factor' :  1};"))
        (a/execute s (str "USE " (name keyspace)))
        (doseq [statement statements]
          (a/execute s statement)))
      (catch Exception _)
      (finally
        (.close ^com.datastax.driver.core.Session s)
        (.close ^com.datastax.driver.core.Cluster cluster)))))

(defn cleanup-fn
  [daemon tmpdir]
  (fn []
    (try
      (.deactivate ^CassandraDaemon daemon)
      (.drain StorageService/instance)
      (jmx/unregister! "org.apache.cassandra.db:type=NativeAccess")
      (recursive-delete tmpdir)
      (catch Exception _))))

(defn start-embedded-cassandra
  [{:keys [port schema]}]
  (let [port          (or port (get-port))
        cassandra-dir (tmpdir "embedded-cassandra-")
        daemon        (activate-daemon cassandra-dir port)
        cleanup!      (cleanup-fn daemon cassandra-dir)]
    (converge-schema schema port)
    (shutdown-hook cleanup!)
    {:port port :cleanup! cleanup!}))

(defn stop-embedded-cassandra
  [{:keys [cleanup!]}]
  (when cleanup!
    (cleanup!)))

(comment
  (start-embedded-cassandra
   {:port   6789
    :schema {:idstore ["CREATE TABLE user (id bigint, name text, PRIMARY KEY (id))"]}})
  )
