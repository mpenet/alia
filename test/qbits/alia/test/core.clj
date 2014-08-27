(ns qbits.alia.test.core
  (:require
   [clojure.test :refer :all]
   [clojure.data :refer :all]
   [qbits.alia :refer :all]
   [qbits.alia.codec :refer :all]
   [qbits.alia.codec.joda-time :refer :all]
   [qbits.alia.codec.eaio-uuid :refer :all]
   [qbits.tardis :refer :all]
   [qbits.hayt :refer :all]
   [clojure.core.async :as async]
   [lamina.core :as lamina]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell])
  (:import
   (com.datastax.driver.core Statement)
   (org.apache.cassandra.service EmbeddedCassandraService)))

(System/setProperty "cassandra.config" (str (io/resource "cassandra.yaml")))
(System/setProperty "cassandra-foreground" "yes")
(System/setProperty "log4j.defaultInitOverride" "false")

(defn start-service!
  []
  ;; cleanup previous runs data
  (println "Clear previous run data")
  (shell/sh "rm" "tmp -rf")
  (println "Starting EmbeddedCassandraService")
  (let [s (EmbeddedCassandraService.)]
    (.start s)
    (println "Service started")
    s))

(def ^:dynamic *cluster*)
(def ^:dynamic *session*)

;; some test data
(def user-data-set [{:created nil
                     :tuuid #uuid "e34288d0-7617-11e2-9243-0024d70cf6c4",
                     :last_name "Penet",
                     :emails #{"m@p.com" "ma@pe.com"},
                     :tags [1 2 3],
                     :first_name "Max",
                     :amap {"foo" 1, "bar" 2},
                     :auuid #uuid "42048d2d-c135-4c18-aa3a-e38a6d3be7f1",
                     :valid true,
                     :birth_year 0,
                     :user_name "mpenet"
                     :tup ["a", "b"]
                     :udt {"foo" "f" "bar" 100}}
                    {:created nil
                     :tuuid #uuid "e34288d0-7617-11e2-9243-0024d70cf6c4",
                     :last_name "Baggins",
                     :emails #{"baggins@gmail.com" "f@baggins.com"},
                     :tags [4 5 6],
                     :first_name "Frodo",
                     :amap {"foo" 1, "bar" 2},
                     :auuid #uuid "1f84b56b-5481-4ee4-8236-8a3831ee5892",
                     :valid true,
                     :birth_year 1,
                     :user_name "frodo"
                     :tup ["a", "b"]
                     :udt {"foo" "f" "bar" 100}}])

;; helpers

(use-fixtures
  :once
  (fn [test-runner]
    (start-service!)
    (flush)
    ;; prepare the thing
    (binding [*cluster* (cluster {:contact-points ["127.0.0.1"] :port 19042})]
      (binding [*session* (connect *cluster*)]
        (try (execute *session* "DROP KEYSPACE alia;")
             (catch Exception _ nil))
        (execute *session* "CREATE KEYSPACE alia WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};")
        (execute *session* "USE alia;")
        (execute *session* "CREATE TYPE udt (
                                foo text,
                                bar bigint
                           )")
        (execute *session* "CREATE TABLE users (
                user_name varchar,
                first_name varchar,
                last_name varchar,
                auuid uuid,
                tuuid timeuuid,
                birth_year bigint,
                created timestamp,
                valid boolean,
                emails set<text>,
                tags list<bigint>,
                amap map<varchar, bigint>,
                tup tuple<varchar, varchar>,
                udt udt,
                PRIMARY KEY (user_name)
              );")
        (execute *session* "CREATE INDEX ON users (birth_year);")

        (execute *session* "INSERT INTO users (user_name, first_name, last_name, emails, birth_year, amap, tags, auuid, tuuid, valid, tup, udt)
       VALUES('frodo', 'Frodo', 'Baggins', {'f@baggins.com', 'baggins@gmail.com'}, 1, {'foo': 1, 'bar': 2}, [4, 5, 6], 1f84b56b-5481-4ee4-8236-8a3831ee5892, e34288d0-7617-11e2-9243-0024d70cf6c4, true, ('a', 'b'),  {foo: 'f', bar: 100});")
        (execute *session* "INSERT INTO users (user_name, first_name, last_name, emails, birth_year, amap, tags, auuid, tuuid, valid, tup, udt)
       VALUES('mpenet', 'Max', 'Penet', {'m@p.com', 'ma@pe.com'}, 0, {'foo': 1, 'bar': 2}, [1, 2, 3], 42048d2d-c135-4c18-aa3a-e38a6d3be7f1, e34288d0-7617-11e2-9243-0024d70cf6c4, true, ('a', 'b'), {foo: 'f', bar: 100});")


        (execute *session* "CREATE TABLE items (
                    id int,
                    si int,
                    text varchar,
                    PRIMARY KEY (id)
                  );")

        (execute *session* "CREATE INDEX ON items (si);")

        (dotimes [i 10]
          (execute *session* (format "INSERT INTO items (id, text, si) VALUES(%s, 'prout', %s);" i i)))

        ;; do the thing
        (test-runner)

        ;; destroy the thing
        (try (execute *session* "DROP KEYSPACE alia;") (catch Exception _ nil))
        (shutdown *session*)
        (shutdown *cluster*)))))

;; (deftest test-string-keys
;;   (is (= (map (fn [user-map]
;;                 (into {} (map (juxt (comp name key) val)
;;                               user-map)))
;;               user-data-set)
;;          (execute *session* "select * from users;" {:string-keys? true}))))

(deftest test-sync-execute
  (is (= user-data-set
         (execute *session* "select * from users;")))

  (is (= user-data-set
         (execute *session* (select :users)))))

(deftest test-async-execute
  ;; promise
  (is (= user-data-set
         @(execute-async *session* "select * from users;")))
  ;; callback
  (let [p (promise)]
    (execute-async *session* "select * from users;"
                   {:success (fn [r] (deliver p r))})
    (is (= user-data-set @p))))

(deftest test-core-async-execute
  (is (= user-data-set
         (async/<!! (execute-chan *session* "select * from users;"))))

  (let [p (promise)]
    (async/take! (execute-chan *session* "select * from users;")
                 (fn [r] (deliver p r)))
    (is (= user-data-set @p)))

;;   ;; Something smarter could be done with alt! (select) but this will
;;   ;; do for a test
  (is (= 3 (count (async/<!! (async/go
                               (loop [i 0 ret []]
                                 (if (= 3 i)
                                   ret
                                   (recur (inc i)
                                          (conj ret (async/<! (execute-chan *session* "select * from users limit 1"))))))))))))

(deftest test-prepared
  (let [s-simple (prepare *session* "select * from users;")
        s-parameterized-simple (prepare *session* (select :users (where {:user_name ?})))
        s-parameterized-in (prepare *session* (select :users (where [[:in :user_name ?]])))
        s-prepare-types (prepare *session*  "INSERT INTO users (user_name, birth_year, auuid, tuuid, created, valid, tags, emails, amap) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);")
        ;; s-parameterized-set (prepare  "select * from users where emails=?;")
        ;; s-parameterized-nil (prepare  "select * from users where session_token=?;")
        ]
    (is (= user-data-set (execute *session* s-simple)))
    (is (= user-data-set (execute *session* s-parameterized-in {:values [["mpenet" "frodo"]]})))
    (is (= [(first user-data-set)]
           (execute *session* s-parameterized-simple {:values ["mpenet"]})))
    ;; manually  bound
    (is (= [(first user-data-set)]
           (execute *session* (bind s-parameterized-simple ["mpenet"]))))
    (is (= [] (execute *session* s-prepare-types {:values ["foobar"
                                                           0
                                                           #uuid "b474e171-7757-449a-87be-d2797d1336e3"
                                                           (qbits.tardis/to-uuid "e34288d0-7617-11e2-9243-0024d70cf6c4")
                                                           (java.util.Date.)
                                                           false
                                                           [1 2 3 4]
                                                           #{"foo" "bar"}
                                                           {"foo" 123}]})))
    (execute *session*  "delete from users where user_name = 'foobar';")))

(deftest test-error
  (let [stmt "slect prout from 1;"]
    (is (:query (try (execute *session* stmt)
                     (catch Exception ex
                       (ex-data ex)))))

    (is (:query (try @(execute-async *session* stmt)
                     (catch Exception ex
                       (ex-data ex)))))

    (is (:query (try @(prepare *session* stmt)
                     (catch Exception ex
                       (ex-data ex)))))

    (let [stmt "select * from foo where bar = ?;" values [1 2]]
      (is (:query (try @(bind (prepare *session* stmt) values)
                       (catch Exception ex
                         (ex-data ex))))))))

(deftest test-lazy-query
  (is (= 10 (count (take 10 (lazy-query *session*
                                        (select :items
                                                (limit 2)
                                                (where {:si (int 0)}))
                                        (fn [q coll]
                                          (merge q (where {:si (-> coll last :si inc)}))))))))

  (is (= 4 (count (take 10 (lazy-query *session*
                                       (select :items
                                               (limit 2)
                                               (where {:si (int 0)}))
                                       (fn [q coll]
                                         (when (< (-> coll last :si) 3)
                                           (merge q (where {:si (-> coll last :si inc)}))))))))))

(defn ^:private get-private-field [instance field-name]
  (.get
   (doto (.getDeclaredField (class instance) field-name)
     (.setAccessible true))
   instance))

(deftest test-fetch-size
  (with-redefs [result-set->maps (fn [result-set string-keys?]
                                   result-set)]
    (let [query "select * from items;"
          result-set (execute *session* query {:fetch-size 3})
          ^Statement statement (get-private-field result-set "statement")]
      (is (= 3 (.getFetchSize statement))))))

(deftest test-fetch-size-async
  (with-redefs [result-set->maps (fn [result-set string-keys?]
                                   result-set)]
    (let [query "select * from items;"
          result-channel (execute-async *session* query {:fetch-size 4})
          result-set (lamina/wait-for-result result-channel)
          ^Statement statement (get-private-field result-set "statement")]
      (is (= 4 (.getFetchSize statement))))))

(deftest test-fetch-size-chan
  (with-redefs [result-set->maps (fn [result-set string-keys?]
                                   result-set)]
    (let [query "select * from items;"
          result-channel (execute-chan *session* query {:fetch-size 5})
          result-set (async/<!! result-channel)
          ^Statement statement (get-private-field result-set "statement")]
      (is (= 5 (.getFetchSize statement))))))

(deftest test-execute-chan-buffered
  (let [ch (execute-chan-buffered *session* "select * from items;" {:fetch-size 5})]
    (is (= 10 (count (loop [coll []]
                       (if-let [row (async/<!! ch)]
                         (recur (cons row coll))
                         coll))))))
  (let [ch (execute-chan-buffered *session* "select * from items;")]
    (is (= 10 (count (loop [coll []]
                       (if-let [row (async/<!! ch)]
                         (recur (cons row coll))
                         coll))))))

  (let [ch (execute-chan-buffered *session* "select * from items;" {:channel (async/chan 5)})]
    (is (= 10 (count (loop [coll []]
                       (if-let [row (async/<!! ch)]
                         (recur (cons row coll))
                         coll)))))))





;; ;; (run-tests)
