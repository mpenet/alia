(ns qbits.alia.test.core
  (:require
   [clojure.test :refer :all]
   [clojure.data :refer :all]
   [qbits.alia :refer :all]
   [qbits.alia.manifold :as ma]
   [qbits.alia.codec :refer :all]
   [qbits.alia.codec.joda-time :refer :all]
   [qbits.alia.codec.eaio-uuid :refer :all]
   [qbits.alia.codec.nippy :refer :all]
   [qbits.tardis :refer :all]
   [qbits.hayt :as h]
   [clojure.core.async :as async])
  (:import
   (com.datastax.driver.core Statement UDTValue)))

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
                     :udt {:foo "f" :bar 100}}
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
                     :udt {:foo "f" :bar 100}}])

;; helpers

(use-fixtures
  :once
  (fn [test-runner]
    ;; prepare the thing
    (binding [*cluster* (cluster {:contact-points ["127.0.0.1"]})]
      (binding [*session* (connect *cluster*)]
        (try (execute *session* "DROP KEYSPACE alia;")
             (catch Exception _ nil))
        (execute *session* "CREATE KEYSPACE alia WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};")
        (execute *session* "USE alia;")
        (execute *session* "CREATE TYPE udt (
                                foo text,
                                bar bigint
                           )")

        (execute *session* "CREATE TYPE udtct (
                                foo text,
                                tup frozen<tuple<varchar, varchar>>
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
                tup frozen<tuple<varchar, varchar>>,
                udt frozen<udt>,
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

        (execute *session* "CREATE TABLE simple (
                    id int,
                    text varchar,
                    PRIMARY KEY (id)
                  );")

        ;; do the thing
        (test-runner)

        ;; destroy the thing
        (try (execute *session* "DROP KEYSPACE alia;") (catch Exception _ nil))
        (shutdown *session*)
        (shutdown *cluster*)))))

(deftest test-sync-execute
  (is (= user-data-set
         (execute *session* "select * from users;")))

  (is (= user-data-set
         (execute *session* (h/select :users)))))

(deftest test-manifold-execute
  ;; promise
  (is (= user-data-set
         @(ma/execute *session* "select * from users;"))))

(deftest test-core-async-execute
  (is (= user-data-set
         (async/<!! (execute-chan *session* "select * from users;"))))

  (let [p (promise)]
    (execute-async *session* "select * from users;"
                   {:success (fn [r] (deliver p r))})
    (is (= user-data-set @p)))

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
        s-parameterized-simple (prepare *session* (h/select :users (h/where {:user_name h/?})))
        s-parameterized-in (prepare *session* (h/select :users (h/where [[:in :user_name h/?]])))
        s-prepare-types (prepare *session*  "INSERT INTO users (user_name, birth_year, auuid, tuuid, created, valid, tags, emails, amap) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);")
        ;; s-parameterized-set (prepare  "select * from users where emails=?;")
        ;; s-parameterized-nil (prepare  "select * from users where session_token=?;")
        ]
    (is (= user-data-set (execute *session* s-simple)))
    (is (= [(first user-data-set)] (execute *session* (h/select :users (h/where {:user_name h/?}))
                                            {:values ["mpenet"]})))
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
    (let [delete-q "delete from users where user_name = 'foobar';"]
      (is (= ()
             (execute *session* (batch (repeat 3 delete-q))))))))

(deftest test-error
  (let [stmt "slect prout from 1;"]
    (is (:query (try (execute *session* stmt)
                     (catch Exception ex
                       (ex-data ex)))))

    (is (:query (try @(prepare *session* stmt)
                     (catch Exception ex
                       (ex-data ex)))))

    (let [stmt "select * from foo where bar = ?;" values [1 2]]
      (is (:query (try @(bind (prepare *session* stmt) values)
                       (catch Exception ex
                         (ex-data ex))))))

    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (execute-chan *session* stmt))))
    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (execute-chan *session* "select * from users;"
                                            {:values ["foo"]}))))
    (is (instance? Throwable
                   (async/<!! (execute-chan *session* "select * from users;"
                                            {:fetch-size :wtf}))))

    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (execute-chan-buffered *session* stmt))))
    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (execute-chan-buffered *session* "select * from users;"
                                            {:values ["foo"]}))))
    (is (instance? Throwable
                   (async/<!! (execute-chan-buffered *session* "select * from users;"
                                            {:retry-policy :wtf}))))

    (is (instance? Throwable
                   (try @(ma/execute *session* "select * from users;"
                                  {:values ["foo"]})
                     (catch Exception ex ex))))

    (is (instance? Throwable
                   (try @(ma/execute *session* "select * from users;"
                                     {:fetch-size :wtf})
                        (catch Exception ex
                          ex))))

    (let [p (promise)]
      (execute-async *session* "select * from users;"
                     {:values  ["foo"]
                      :error (fn [r] (deliver p r))})
      (is (:query (ex-data @p))))

    (let [p (promise)]
      (execute-async *session* "select * from users;"
                          {:fetch-size :wtf
                           :error (fn [r] (deliver p r))})
      (instance? Throwable @p))))

(deftest test-lazy-query
  (is (= 10 (count (take 10 (lazy-query *session*
                                        (h/select :items
                                                (h/limit 2)
                                                (h/where {:si (int 0)}))
                                        (fn [q coll]
                                          (merge q (h/where {:si (-> coll last :si inc)}))))))))

  (is (= 4 (count (take 10 (lazy-query *session*
                                       (h/select :items
                                               (h/limit 2)
                                               (h/where {:si (int 0)}))
                                       (fn [q coll]
                                         (when (< (-> coll last :si) 3)
                                           (merge q (h/where {:si (-> coll last :si inc)}))))))))))

(defn ^:private get-private-field [instance field-name]
  (.get
   (doto (.getDeclaredField (class instance) field-name)
     (.setAccessible true))
   instance))

(deftest test-fetch-size
  (with-redefs [result-set->maps (fn [result-set _ _] result-set)]
    (let [query "select * from items;"
          result-set (execute *session* query {:fetch-size 3})
          ^Statement statement (get-private-field result-set "statement")]
      (is (= 3 (.getFetchSize statement))))))

(deftest test-fetch-size-chan
  (with-redefs [result-set->maps (fn [result-set _ _] result-set)]
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
                         coll))))))

  (let [ch (execute-chan-buffered *session* "select * from items;" {:fetch-size 1})]
    (is (= 1 (count (loop [coll []]
                      (if-let [row (async/<!! ch)]
                        (do
                          (async/close! ch)
                          (recur (cons row coll)))
                        coll)))))))

(deftest test-named-bindings
  (let [prep-write (prepare *session* "INSERT INTO simple (id, text) VALUES(:id, :text);")
        prep-read (prepare *session* "SELECT * FROM simple WHERE id = :id;")
        an-id (int 100)]

    (is (= []
           (execute *session* prep-write {:values {:id   an-id
                                                   :text "inserted via named bindings"}})))

    (is (= [{:id   an-id
             :text "inserted via named bindings"}]
           (execute *session* prep-read {:values {:id an-id}})))))


(deftest test-udt-encoder
  (let [encoder (udt-encoder *session* :udt)
        encoder-ct (udt-encoder *session* :udtct)
        tup (tuple-encoder *session* :users :tup)]
    (is (instance? UDTValue (encoder {:foo "f" "bar" 100})))
    (is (instance? UDTValue (encoder {:foo nil "bar" 100})))
    (is (instance? UDTValue (encoder {:foo nil "bar" 100})))
    (is (instance? UDTValue (encoder-ct {:foo "f" :tup (tup ["a" "b"])})))
    (is (= :qbits.alia.codec.udt/type-not-found
           (-> (try (udt-encoder *session* :invalid-type) (catch Exception e e))
               ex-data
               :type)))
    (is (= :qbits.alia.codec.tuple/type-not-found
           (-> (try (tuple-encoder *session* :users :invalid-type) (catch Exception e e))
               ex-data
               :type)))
    (is (= :qbits.alia.codec.tuple/type-not-found
           (-> (try (tuple-encoder *session* :invalid-col :invalid-type) (catch Exception e e))
               ex-data
               :type)))))

(deftest test-result-set-types
  (is (instance? clojure.lang.LazySeq (execute *session* "select * from items;")))
  (is (instance? clojure.lang.PersistentVector (execute *session* "select * from items;"
                                                        {:result-set-fn #(into [])}))))
