(ns qbits.alia-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [clojure.data]
   [clj-time.core :as clj-time]
   [qbits.alia :as alia]
   [qbits.alia.cql-session :as cql-session]
   [qbits.alia.manifold :as alia.manifold]
   [qbits.alia.async :as alia.async]
   [qbits.alia.codec.default :as codec.default]
   [qbits.alia.codec.udt-aware :as codec.udt-aware]
   ;; [qbits.alia.codec.extension.joda-time :as codec.]
   [qbits.hayt :as h]
   [clojure.core.async :as async]
   [manifold.stream :as stream])
  (:import
   [java.time Instant]
   [com.datastax.oss.driver.api.core
    DefaultConsistencyLevel]
   [com.datastax.oss.driver.api.core.data TupleValue UdtValue]))

(try
  (require 'qbits.alia.spec)
  (require 'clojure.spec.test.alpha)
  ((resolve 'clojure.spec.test.alpha/instrument))
  (println "Instrumenting qbits.alia with clojure.spec")
  (catch Exception e
    (.printStackTrace e)))

(def ^:dynamic *session*)

;; some test data
(def user-data-set [{:created (Instant/parse "2012-04-18T11:23:12.345Z")
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
                    {:created (Instant/parse "2012-04-19T12:18:09.678Z")
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

(def sort-result (partial sort-by :user_name))

(defn setup-test-keyspace
  [session]
  ;; (try (alia/execute session "DROP KEYSPACE IF EXISTS alia;") (catch Exception _ nil))
  (alia/execute session "CREATE KEYSPACE IF NOT EXISTS alia WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};")
  (alia/execute session "USE alia;")
  (alia/execute session "CREATE TYPE IF NOT EXISTS udt (
                                foo text,
                                bar bigint
                           )")

  (alia/execute session "CREATE TYPE IF NOT EXISTS udtct (
                                foo text,
                                tup frozen<tuple<varchar, varchar>>
                           )")
  (alia/execute session "CREATE TABLE IF NOT EXISTS users (
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
  (alia/execute session "CREATE INDEX IF NOT EXISTS ON users (birth_year);")

  (alia/execute session "INSERT INTO users (user_name, first_name, last_name, emails, birth_year, amap, tags, auuid, tuuid, valid, tup, udt, created)
       VALUES('frodo', 'Frodo', 'Baggins', {'f@baggins.com', 'baggins@gmail.com'}, 1, {'foo': 1, 'bar': 2}, [4, 5, 6], 1f84b56b-5481-4ee4-8236-8a3831ee5892, e34288d0-7617-11e2-9243-0024d70cf6c4, true, ('a', 'b'),  {foo: 'f', bar: 100}, '2012-04-19T12:18:09.678Z');")
  (alia/execute session "INSERT INTO users (user_name, first_name, last_name, emails, birth_year, amap, tags, auuid, tuuid, valid, tup, udt, created)
       VALUES('mpenet', 'Max', 'Penet', {'m@p.com', 'ma@pe.com'}, 0, {'foo': 1, 'bar': 2}, [1, 2, 3], 42048d2d-c135-4c18-aa3a-e38a6d3be7f1, e34288d0-7617-11e2-9243-0024d70cf6c4, true, ('a', 'b'), {foo: 'f', bar: 100}, '2012-04-18T11:23:12.345Z');")


  (alia/execute session "CREATE TABLE IF NOT EXISTS items (
                    id int,
                    si int,
                    text varchar,
                    PRIMARY KEY (id)
                  );")

  (alia/execute session "CREATE INDEX IF NOT EXISTS ON items (si);")

  (dotimes [i 10]
    (alia/execute session (format "INSERT INTO items (id, text, si) VALUES(%s, 'prout', %s);" i i)))

  (alia/execute session "CREATE TABLE IF NOT EXISTS simple (
                    id int,
                    text varchar,
                    PRIMARY KEY (id)
                  );"))

(defn teardown-test-keyspace
  [session]
  (try (alia/execute *session* "DROP KEYSPACE alia;") (catch Exception _ nil)))

(t/use-fixtures
  :once
  (fn [test-runner]
    (binding [*session* (alia/session {:session-keyspace "alia"})]

      (setup-test-keyspace *session*)

      ;; do the thing
      (test-runner)

      ;; (teardown-test-keyspace *session* )

      (alia/shutdown *session*))))

(deftest execute-test
  (testing "string query"
    (is (= user-data-set
           (alia/execute *session* "select * from users;"))))
  (testing "hayt query"
    (is (= user-data-set
           (alia/execute *session* (h/select :users))))))

(deftest execute-async-test
  (let [{current-page :current-page} @(alia/execute-async *session* "select * from users;")]
    (is (= user-data-set
           current-page))))

(deftest manifold-test
  (testing "promise"
    (is (= user-data-set
           @(alia.manifold/execute *session* "select * from users;"))))
  (testing "stream"
    (let [r-s (alia.manifold/execute-buffered *session* "select * from users;")
          rs @(manifold.stream/reduce conj [] r-s)]
      (is (= user-data-set
             rs)))))

(deftest core-async-test
  (testing "promise-chan"
    (is (= user-data-set
           (async/<!! (alia.async/execute-chan *session* "select * from users;")))))

  (testing "chan of records"
    (let [r-c (alia.async/execute-chan-buffered *session* "select * from users;")
          rs-c (async/reduce conj [] r-c)
          rs (async/<!! rs-c)]
      (is (= user-data-set
             rs))))

;;   ;; Something smarter could be done with alt! (select) but this will
;;   ;; do for a test
  ;; (is (= 3 (count (async/<!! (async/go
  ;;                              (loop [i 0 ret []]
  ;;                                (if (= 3 i)
  ;;                                  ret
  ;;                                  (recur (inc i)
  ;;                                         (conj ret (async/<! (execute-chan *session* "select * from users limit 1")))))))))))

  )

(deftest prepare-test
  (testing "simple"
    (let [s-simple (alia/prepare *session* "select * from users;")]
      (is (= user-data-set (alia/execute *session* s-simple)))))

  (testing "paramterized simple"
    (is (= [(first user-data-set)]
           (alia/execute *session*
                         (h/select :users (h/where {:user_name h/?}))
                         {:values ["mpenet"]})))
    (let [s-parameterized-simple (alia/prepare
                                  *session*
                                  (h/select :users (h/where {:user_name h/?})))]

      (is (= [(first user-data-set)]
             (alia/execute *session*
                           s-parameterized-simple
                           {:values ["mpenet"]})))
      ;; manually bound
      (is (= [(first user-data-set)]
             (alia/execute *session*
                           (alia/bind s-parameterized-simple ["mpenet"]))))))

  (testing "parameterized in"
    (let [s-parameterized-in (alia/prepare
                              *session*
                              (h/select :users (h/where [[:in :user_name h/?]])))]
      (is (= (sort-result user-data-set)
             (sort-result
              (alia/execute *session*
                            s-parameterized-in
                            {:values [["mpenet" "frodo"]]}))))))

  (testing "prepare types"
    (let [s-prepare-types (alia/prepare
                           *session*
                           "INSERT INTO users (user_name, birth_year, auuid, tuuid, created, valid, tags, emails, amap) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);")]

      (testing "seq of values"
        (is (= [] (alia/execute
                   *session*
                   s-prepare-types
                   {:values ["foobar"
                             0
                             #uuid "b474e171-7757-449a-87be-d2797d1336e3"
                             #uuid "e34288d0-7617-11e2-9243-0024d70cf6c4"
                             (Instant/now)
                             false
                             [1 2 3 4]
                             #{"foo" "bar"}
                             {"foo" 123}]})))
        (let [delete-q "delete from users where user_name = 'foobar';"]
          (is (= ()
                 (alia/execute *session* (alia/batch (repeat 3 delete-q)))))))

      (testing "map of values"
        (is (= ()
               (alia/execute
                *session*
                s-prepare-types
                {:values {:user_name "barfoo"
                          :birth_year 0
                          :auuid #uuid "b474e171-7757-449a-87be-d2797d1336e3"
                          :tuuid #uuid "e34288d0-7617-11e2-9243-0024d70cf6c4"
                          :created (Instant/now)
                          :valid false
                          :tags [1 2 3 4]
                          :emails  #{"foo" "bar"}
                          :amap {"foo" 123}}})))
        (let [delete-q "delete from users where user_name = 'barfoo';"]
          (is (= ()
                 (alia/execute *session* (alia/batch (repeat 3 delete-q))))))

        (is (= []
               (alia/execute
                *session*
                s-prepare-types
                {:values {:user_name "ffoooobbaarr"
                          :birth_year 0
                          :auuid #uuid "b474e171-7757-449a-87be-d2797d1336e3"
                          :tuuid #uuid "e34288d0-7617-11e2-9243-0024d70cf6c4"
                          :created (Instant/now)
                          :valid false
                          :tags [1 2 3 4]
                          :emails  #{"foo" "bar"}
                          :amap {"foo" 123}}})))

        (let [delete-q "delete from users where user_name = 'ffoooobbaarr';"]
          (is (= ()
                 (alia/execute *session* (alia/batch (repeat 3 delete-q)))))))))

  (testing "parameterized set"
    (let [;; s-parameterized-set (prepare  "select * from users where emails=?;")
          ]))
  (testing "parameterized nil"
    (let [;; s-parameterized-nil (prepare  "select * from users where session_token=?;")
          ])))

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
    (is (instance? Exception
                   (async/<!! (execute-chan *session* "select * from users;"
                                            {:fetch-size :wtf}))))

    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (execute-chan-buffered *session* stmt))))
    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (execute-chan-buffered *session* "select * from users;"
                                            {:values ["foo"]}))))
    (is (instance? Exception
                   (async/<!! (execute-chan-buffered *session* "select * from users;"
                                            {:retry-policy :wtf}))))

    (is (instance? Exception
                   (try @(ma/execute *session* "select * from users;"
                                  {:values ["foo"]})
                     (catch Exception ex ex))))

    (is (instance? Exception
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
      (instance? Exception @p))))

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


(let [get-private-field
      (fn [instance field-name]
        (.get
         (doto (.getDeclaredField (class instance) field-name)
           (.setAccessible true))
         instance))

      result-set-fn-with-execution-infos
      (fn [rs]
        (vary-meta rs assoc
                   :execution-info (execution-info rs)))

      get-fetch-size
      (fn [rs]
        (-> rs meta :execution-info first
            (get-private-field "statement")
            .getFetchSize))]

  (deftest test-fetch-size
    (let [result-set (execute *session*
                              "select * from items;"
                              {:fetch-size 3
                               :result-set-fn result-set-fn-with-execution-infos})]
      (is (= 3 (get-fetch-size result-set)))))

  (deftest test-fetch-size-chan
    (let [result-ch (execute-chan *session*
                                       "select * from items;"
                                       {:fetch-size 5
                                        :result-set-fn result-set-fn-with-execution-infos})]
      (is (= 5 (get-fetch-size (async/<!! result-ch)))))))

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
           (execute *session* prep-write {:values {:id an-id
                                                   :text "inserted via named bindings"}})))

    (is (= [{:id an-id
             :text "inserted via named bindings"}]
           (execute *session* prep-read {:values {:id an-id}})))

    (is (= [{:id an-id
             :text "inserted via named bindings"}]
           (execute *session* "SELECT * FROM simple WHERE id = :id;"
                    {:values {:id an-id}})))))

(deftest test-udt-encoder
  (let [encoder (udt-encoder *session* :udt)
        encoder-ct (udt-encoder *session* :udtct)
        tup (tuple-encoder *session* :users :tup)]
    (is (instance? UdtValue (encoder {:foo "f" "bar" 100})))
    (is (instance? UdtValue (encoder {:foo nil "bar" 100})))
    (is (instance? UdtValue (encoder {:foo nil "bar" 100})))
    (is (instance? UdtValue (encoder-ct {:foo "f" :tup (tup ["a" "b"])})))
    (is (= :qbits.alia.udt/type-not-found
           (-> (try (udt-encoder *session* :invalid-type) (catch Exception e e))
               ex-data
               :type)))
    (is (= :qbits.alia.tuple/type-not-found
           (-> (try (tuple-encoder *session* :users :invalid-type) (catch Exception e e))
               ex-data
               :type)))
    (is (= :qbits.alia.tuple/type-not-found
           (-> (try (tuple-encoder *session* :invalid-col :invalid-type) (catch Exception e e))
               ex-data
               :type)))))

(deftest test-result-set-types
  (is (instance? clojure.lang.LazySeq (execute *session* "select * from items;")))
  (is (instance? clojure.lang.PersistentVector (execute *session* "select * from items;"
                                                        {:result-set-fn #(into [] %)}))))

(defrecord Foo [foo bar])
(deftest test-udt-registry
  (let [codec qbits.alia.codec.udt-aware/codec]
    (qbits.alia.codec.udt-aware/register-udt! codec *session* :udt Foo)
    (is
     (= Foo
        (-> (execute *session* "select * from users limit 1"
                     {:codec codec})
            first
            :udt
            type)))
    (qbits.alia.codec.udt-aware/deregister-udt! codec *session* :udt Foo)))

(deftest test-custom-codec
  (is (-> (execute *session* "select * from users limit 1"
                   {:codec {:decoder (constantly 42)
                            :encoder identity}})
          first
          :valid
          (= 42)))
    ;; todo test encoder
  )
