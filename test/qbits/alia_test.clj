(ns qbits.alia-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [clojure.data]
   [qbits.alia :as alia]
   [qbits.alia.manifold :as alia.manifold]
   [qbits.alia.result-set :as result-set]
   [qbits.alia.async :as alia.async]
   [qbits.alia.codec.udt-aware :as codec.udt-aware]
   ;; [qbits.alia.codec.extension.joda-time :as codec.]
   [qbits.hayt :as h]
   [clojure.core.async :as async]
   [manifold.stream :as stream])
  (:import
   [java.time Instant]
   [java.util UUID]
   [java.nio ByteBuffer]
   [com.datastax.oss.driver.api.core.cql ExecutionInfo]
   [com.datastax.oss.driver.api.core.data UdtValue TupleValue]))

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
                  );")

  (alia/execute session "CREATE TABLE IF NOT EXISTS namespaced (
                    \"namespaced/id\" int,
                    \"namespaced/text\" varchar,
                    PRIMARY KEY (\"namespaced/id\")
                  );")

  (alia/execute session "CREATE TABLE IF NOT EXISTS reserved (
                    \"view\" int,
                    \"all\" varchar,
                    PRIMARY KEY (\"view\")
                  );")

  (alia/execute session "CREATE TABLE IF NOT EXISTS blobs (id uuid, data blob, PRIMARY KEY (id));"))

(defn teardown-test-keyspace
  [session]
  (try (alia/execute *session* "DROP KEYSPACE alia;") (catch Exception _ nil)))

(t/use-fixtures
  :once
  (fn [test-runner]
    (binding [*session* (alia/session
                         {
                          ;; don't specify the keyspace - it doesn't exist yet on CI
                          ;; :session-keyspace "alia"
                          })]

      (setup-test-keyspace *session*)

      ;; do the thing
      (test-runner)

      ;; (teardown-test-keyspace *session* )

      (alia/close *session*))))

(deftest execute-test
  (testing "string query"
    (is (= user-data-set
           (alia/execute *session* "select * from users;"))))
  (testing "hayt query"
    (is (= user-data-set
           (alia/execute *session* (h/select :users)))))
  (testing "errors"
    (let [stmt "slect prout from 1;"]
      (is (:query (try (alia/execute *session* stmt)
                       (catch Exception ex
                         (ex-data ex)))))))
  (testing "test-result-set-types"
    (is (instance? clojure.lang.LazySeq
                   (alia/execute *session* "select * from items;")))
    (is (instance? clojure.lang.PersistentVector
                   (alia/execute
                    *session*
                    "select * from items;"
                    {:result-set-fn #(into [] %)}))))

  (testing "page-size"
    (let [result-set-fn-with-execution-infos
          (fn [rs]
            (vary-meta rs assoc
                       :execution-infos (result-set/execution-infos rs)))

          get-page-size
          (fn [rs]
            (let [^ExecutionInfo xi (-> rs meta :execution-infos first)]
              (-> xi .getStatement .getPageSize)))

          result-set (alia/execute
                      *session*
                      "select * from items;"
                      {:page-size 3
                       :result-set-fn result-set-fn-with-execution-infos})]
      (is (= 3 (get-page-size result-set)))))

  (testing "test-custom-codec"
    (is (-> (alia/execute *session* "select * from users limit 1"
                          {:codec {:decoder (constantly 42)
                                   :encoder identity}})
            first
            :valid
            (= 42)))
    ;; TODO test encoder
    )

  (testing "specify a row-generator"
    (let [records (alia/execute
                   *session*
                   "select * from items where id in (1,3)"
                   {:row-generator result-set/row-gen->ns-map})]
      (is (= #{{:items/id 1
                :items/si 1
                :items/text "prout"}
               {:items/id 3
                :items/si 3
                :items/text "prout"}}
             (set records))))))

(deftest execute-async-test
  (testing "success"
    (let [{current-page :current-page} @(alia/execute-async
                                         *session*
                                         "select * from users;")]
      (is (= user-data-set
             current-page))))
  (testing "errors"
    (let [stmt "slect prout from 1;"
          exd (try
                @(alia/execute-async *session* stmt)
                (catch Exception ex
                  ;; remove j.u.c.ExecutionException wrapper to get to ex-info
                  (ex-data (ex-cause ex))))]
      (is (some? (:query exd) ))))

  (testing "page-size"
    (let [r @(alia/execute-async
              *session*
              "select * from items"
              {:page-size 3})
          ^ExecutionInfo xi (result-set/execution-info r)
          page-size (-> xi .getStatement .getPageSize)]
      (is (= 3 page-size)))))

(deftest manifold-test
  (testing "deferred"
    (is (= user-data-set
           @(alia.manifold/execute *session* "select * from users;"))))
  (testing "stream of pages"
    (let [r-s (alia.manifold/execute-stream-pages *session* "select * from users;")
          rs @(manifold.stream/take! r-s)]
      (is (= user-data-set
             rs))))
  (testing "stream of records"
    (let [r-s (alia.manifold/execute-stream *session* "select * from users;")
          rs @(manifold.stream/reduce conj [] r-s)]
      (is (= user-data-set
             rs))))
  (testing "errors"
    (is (instance? Exception
                   (try @(alia.manifold/execute
                          *session*
                          "slect prout from 1;")
                        (catch Exception ex ex))))

    (is (instance? Exception
                   (try @(alia.manifold/execute
                          *session*
                          "select * from users;"
                          {:values ["foo"]})
                        (catch Exception ex ex))))

    (is (instance? Exception
                   (try @(alia.manifold/execute
                          *session*
                          "select * from users;"
                          {:page-size :wtf})
                        (catch Exception ex
                          ex))))

    (is (instance? Exception
                   @(manifold.stream/take!
                     (alia.manifold/execute-stream-pages
                      *session*
                      "select * from users;"
                      {:page-size :wtf}))))

    (is (instance? Exception
                   @(manifold.stream/take!
                     (alia.manifold/execute-stream
                      *session*
                      "select * from users;"
                      {:page-size :wtf}))))))

(deftest core-async-test
  (testing "promise-chan"
    (is (= user-data-set
           (async/<!! (alia.async/execute *session* "select * from users;")))))

  (testing "chan of pages"
    (let [r-c (alia.async/execute-chan-pages *session* "select * from users;")
          rs (async/<!! r-c)]
      (is (= user-data-set
             rs))))

  (testing "chan of records"
    (let [r-c (alia.async/execute-chan *session* "select * from users;")
          rs-c (async/reduce conj [] r-c)
          rs (async/<!! rs-c)]
      (is (= user-data-set
             rs)))

    (let [ch (alia.async/execute-chan
              *session*
              "select * from items;")]
      (is (= 10 (count (loop [coll []]
                         (if-let [row (async/<!! ch)]
                           (recur (cons row coll))
                           coll))))))

    (testing "supplied channel"
      (let [ch (alia.async/execute-chan
                *session*
                "select * from items;"
                {:channel (async/chan 5)})]
        (is (= 10 (count (loop [coll []]
                           (if-let [row (async/<!! ch)]
                             (recur (cons row coll))
                             coll)))))))

    (testing "page-sizes"
      (let [ch (alia.async/execute-chan
                *session*
                "select * from items;"
                {:page-size 5})]
        (is (= 10 (count (loop [coll []]
                           (if-let [row (async/<!! ch)]
                             (recur (cons row coll))
                             coll))))))

      (let [ch (alia.async/execute-chan
                *session*
                "select * from items;"
                {:page-size 1})]
        (is (= 1 (count (loop [coll []]
                          (if-let [row (async/<!! ch)]
                            (do
                              (async/close! ch)
                              (recur (cons row coll)))
                            coll))))))))

  ;;   ;; Something smarter could be done with alt! (select) but this will
  ;;   ;; do for a test
  ;; (is (= 3 (count (async/<!! (async/go
  ;;                              (loop [i 0 ret []]
  ;;                                (if (= 3 i)
  ;;                                  ret
  ;;                                  (recur (inc i)
  ;;                                         (conj ret (async/<! (execute-chan *session* "select * from users limit 1")))))))))))


  (testing "errors with execute-chan"
    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (alia.async/execute-chan
                               *session*
                               "slect prout from 1;"))))

    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (alia.async/execute-chan
                               *session*
                               "select * from users;"
                               {:values ["foo"]}))))
    (is (instance? Exception
                   (async/<!! (alia.async/execute-chan
                               *session*
                               "select * from users;"
                               {:page-size :wtf}))))

    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (alia.async/execute-chan-pages
                               *session*
                               "select * from users;"
                               {:page-size :wtf}))))

    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (alia.async/execute-chan
                               *session*
                               "select * from users;"
                               {:page-size :wtf})))))

  (testing "errors with execute"
    (is (instance? clojure.lang.ExceptionInfo
                   (async/<!! (alia.async/execute
                               *session*
                               "slect prout from 1;"))))))

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
        (is (= nil (alia/execute
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
          (is (= nil
                 (alia/execute *session* (alia/batch (repeat 3 delete-q)))))))

      (testing "map of values"
        (is (= nil
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
          (is (= nil
                 (alia/execute *session* (alia/batch (repeat 3 delete-q))))))

        (is (= nil
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
          (is (= nil
                 (alia/execute *session* (alia/batch (repeat 3 delete-q)))))))))

  (testing "named-bindings"
    (let [prep-write (alia/prepare *session* "INSERT INTO simple (id, text) VALUES(:id, :text);")
          prep-read (alia/prepare *session* "SELECT * FROM simple WHERE id = :id;")
          an-id (int 100)]

      (is (= nil
             (alia/execute *session* prep-write {:values {:id an-id
                                                          :text "inserted via named bindings"}})))

      (is (= [{:id an-id
               :text "inserted via named bindings"}]
             (alia/execute *session* prep-read {:values {:id an-id}})))

      (is (= [{:id an-id
               :text "inserted via named bindings"}]
             (alia/execute *session* "SELECT * FROM simple WHERE id = :id;"
                           {:values {:id an-id}})))))

  (testing "reserved-names"
    (let [prep-write (alia/prepare *session* "INSERT INTO reserved (\"view\", \"all\") VALUES(:\"view\", :\"all\");")
          prep-read (alia/prepare *session* "SELECT * FROM reserved WHERE \"view\" = :\"view\";")
          an-id (int 100)]

      (is (= nil
             (alia/execute *session* prep-write {:values {:view an-id
                                                          :all "inserted via reserved bindings"}})))

      (is (= [{:view an-id
               :all "inserted via reserved bindings"}]
             (alia/execute *session* prep-read {:values {:view an-id}})))

      (is (= [{:view an-id
               :all "inserted via reserved bindings"}]
             (alia/execute *session* "SELECT * FROM reserved WHERE \"view\" = :\"view\";"
                           {:values {:view an-id}})))))

  (testing "namespaced-named-bindings"
    (let [prep-write (alia/prepare *session* "INSERT INTO namespaced (\"namespaced/id\", \"namespaced/text\") VALUES(:\"namespaced/id\", :\"namespaced/text\");")
          prep-read (alia/prepare *session* "SELECT * FROM namespaced WHERE \"namespaced/id\" = :\"namespaced/id\";")
          an-id (int 100)]


      (is (= nil
             (alia/execute *session* prep-write {:values {:namespaced/id an-id
                                                          :namespaced/text "inserted via namespaced named bindings"}})))

      (is (= [{:namespaced/id an-id
               :namespaced/text "inserted via namespaced named bindings"}]
             (alia/execute *session* prep-read {:values {:namespaced/id an-id}})))

      (is (= [{:namespaced/id an-id
               :namespaced/text "inserted via namespaced named bindings"}]
             (alia/execute *session* "SELECT * FROM namespaced WHERE \"namespaced/id\" = :\"namespaced/id\";"
                           {:values {:namespaced/id an-id}})))))

  (testing "parameterized set"
    (let [;; s-parameterized-set (prepare  "select * from users where emails=?;")
          ]))
  (testing "parameterized nil"
    (let [;; s-parameterized-nil (prepare  "select * from users where session_token=?;")
          ]))

  (testing "errors"
    (let [stmt "slect prout from 1;"]


      (is (:query (try @(alia/prepare *session* stmt)
                       (catch Exception ex
                         (ex-data ex)))))

      (let [stmt "select * from foo where bar = ?;" values [1 2]]
        (is (:query (try @(alia/bind (alia/prepare *session* stmt) values)
                         (catch Exception ex
                           (ex-data ex)))))))))

(deftest lazy-query-test
  (is (= 10
         (count
          (take 10
                (alia/lazy-query
                 *session*
                 (h/select :items
                           (h/limit 2)
                           (h/where {:si (int 0)}))
                 (fn [q coll]
                   (merge q (h/where {:si (-> coll last :si inc)}))))))))

  (is (= 4
         (count
          (take 10
                (alia/lazy-query
                 *session*
                 (h/select :items
                           (h/limit 2)
                           (h/where {:si (int 0)}))
                 (fn [q coll]
                   (when (< (-> coll last :si) 3)
                     (merge q (h/where {:si (-> coll last :si inc)}))))))))))

(defrecord Foo [foo bar])

(deftest udt-encoder-test
  (testing "explicit udt-encoder"
    (let [encoder (alia/udt-encoder *session* :udt)
          encoder-ct (alia/udt-encoder *session* :udtct)
          tup (alia/tuple-encoder *session* :users :tup)]
      (is (instance? UdtValue (encoder {:foo "f" "bar" 100})))
      (is (instance? UdtValue (encoder {:foo nil "bar" 100})))
      (is (instance? UdtValue (encoder {:foo nil "bar" 100})))
      (is (instance? UdtValue (encoder-ct {:foo "f" :tup (tup ["a" "b"])})))
      (is (= :qbits.alia.udt/udt-not-found
             (-> (try (alia/udt-encoder *session* :invalid-type) (catch Exception e e))
                 ex-data
                 :type)))
      (is (= :qbits.alia.tuple/tuple-not-found
             (-> (try (alia/tuple-encoder *session* :users :invalid-type) (catch Exception e e))
                 ex-data
                 :type)))))

  (testing "udt-registry"
    (let [codec qbits.alia.codec.udt-aware/codec]
      (codec.udt-aware/register-udt! codec *session* :udt Foo)

      (is
       (= Foo
          (-> (alia/execute *session* "select * from users limit 1"
                            {:codec codec})
              first
              :udt
              type)))

      (codec.udt-aware/deregister-udt! codec *session* :udt Foo))))


(deftest tuple-encoder-test
  (let [tup (alia/tuple-encoder *session* :users :tup)]
    (is (instance? TupleValue (tup  ["a" "b"])))

    (is (= :qbits.alia.tuple/tuple-not-found
           (-> (try (alia/tuple-encoder *session* :invalid-col :invalid-type) (catch Exception e e))
               ex-data
               :type)))))

(deftest blob-test
  (testing "basic blob interactions"
    (let [id (UUID/randomUUID)
          bytes (byte-array (shuffle (range 256)))
          insert-statement (alia/prepare *session* "INSERT INTO blobs (id, data) VALUES (?, ?);")]
      (alia/execute *session* insert-statement {:values [id bytes]})
      (let [row (->> (alia/execute *session* "SELECT * FROM blobs WHERE id = :id;"
                         {:values {:id id}})
                     (first))
            ^ByteBuffer data (:data row)
            row-bytes (.array data)]
        (is (= (vec bytes) (vec row-bytes)))))))
