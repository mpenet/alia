(ns qbits.alia.test.core
  (:use clojure.test
        clojure.data
        qbits.alia
        qbits.alia.codec.joda-time
        qbits.alia.codec.eaio-uuid
        qbits.tardis
        qbits.hayt
        qbits.alia.test.embedded
        clojure.core.async))

(def ^:dynamic *cluster*)

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
                     :user_name "mpenet"}
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
                     :user_name "frodo"}])

;; helpers

(use-fixtures
  :once
  (fn [test-runner]
    (flush)
    ;; prepare the thing
    (binding [*cluster* (cluster "127.0.0.1" :port 19042)]
      (with-session (connect *cluster*)
        (try (execute "DROP KEYSPACE alia;")
             (catch Exception _ nil))
        (execute  "CREATE KEYSPACE alia WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};")
        (execute "USE alia;")
        (execute "CREATE TABLE users (
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
                PRIMARY KEY (user_name)
              );")
        (execute "CREATE INDEX ON users (birth_year);")

        (execute "INSERT INTO users (user_name, first_name, last_name, emails, birth_year, amap, tags, auuid, tuuid, valid)
       VALUES('frodo', 'Frodo', 'Baggins', {'f@baggins.com', 'baggins@gmail.com'}, 1, {'foo': 1, 'bar': 2}, [4, 5, 6], 1f84b56b-5481-4ee4-8236-8a3831ee5892, e34288d0-7617-11e2-9243-0024d70cf6c4, true);")
        (execute "INSERT INTO users (user_name, first_name, last_name, emails, birth_year, amap, tags, auuid, tuuid, valid)
       VALUES('mpenet', 'Max', 'Penet', {'m@p.com', 'ma@pe.com'}, 0, {'foo': 1, 'bar': 2}, [1, 2, 3], 42048d2d-c135-4c18-aa3a-e38a6d3be7f1, e34288d0-7617-11e2-9243-0024d70cf6c4, true);")


        (execute "CREATE TABLE items (
                    id int,
                    si int,
                    text varchar,
                    PRIMARY KEY (id)
                  );")

        (execute "CREATE INDEX ON items (si);")

        (dotimes [i 10]
          (execute (format "INSERT INTO items (id, text, si) VALUES(%s, 'prout', %s);" i i)))

        ;; do the thing
        (test-runner)

        ;; destroy the thing
        (try (execute "DROP KEYSPACE alia;") (catch Exception _ nil))
        (shutdown)
        (shutdown *cluster*)))))

(deftest test-keywordize
  (is (= (map (fn [user-map]
                (into {} (map (juxt (comp name key) val)
                              user-map)))
              user-data-set)
         (execute "select * from users;" :keywordize? false))))

(deftest test-sync-execute
  (is (= user-data-set
         (execute "select * from users;")
         (execute (connect *cluster* "alia") "select * from users;")))

    (is (= user-data-set
         (execute (select :users)))))

(deftest test-async-execute
  ;; promise
  (is (= user-data-set
         @(execute-async "select * from users;")))
  ;; callback
  (let [p (promise)]
    (execute-async  "select * from users;"
                    :success (fn [r] (deliver p r)))
    (is (= user-data-set @p))))

(deftest test-core-async-execute
  (is (= user-data-set
         (<!! (execute-async-chan "select * from users;"))))
  ;; Something smarter could be done with alt! (select) but this will
  ;; do for a test
  (is (= 3 (count (<!! (go
                        (loop [i 0 ret []]
                          (if (= 3 i)
                            ret
                            (recur (inc i)
                                   (conj ret (<! (execute-async-chan "select * from users limit 1"))))))))))))

(deftest test-prepared
  (let [s-simple (prepare "select * from users;")
        s-parameterized-simple (prepare (select :users (where {:user_name :foo})))
        s-prepare-types (prepare "INSERT INTO users (user_name, birth_year, auuid, tuuid, created, valid, tags, emails, amap) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?);")
        ;; s-parameterized-set (prepare  "select * from users where emails=?;")
        ;; s-parameterized-nil (prepare  "select * from users where session_token=?;")
        ]
    (is (= user-data-set (execute s-simple)))
    (is (= [(first user-data-set)]
           (execute s-parameterized-simple :values ["mpenet"])))
    ;; manually  bound
    (is (= [(first user-data-set)]
           (execute (bind s-parameterized-simple ["mpenet"]))))
    (is (= [(first user-data-set)]
           (execute s-parameterized-simple :values [:mpenet])))
    (is (= [] (execute s-prepare-types :values ["foobar"
                                                0
                                                #uuid "b474e171-7757-449a-87be-d2797d1336e3"
                                                (qbits.tardis/to-uuid "e34288d0-7617-11e2-9243-0024d70cf6c4")
                                                (java.util.Date.)
                                                false
                                                [1 2 3 4]
                                                #{"foo" "bar"}
                                                {"foo" 123}])))
    (execute  "delete from users where user_name = 'foobar';") ;; cleanup

    ;; ;; index on collections not supp  orted yet
    ;; (is (= [(first user-data-set)]
    ;;        (execute  (bind s-parameterized-set #{"m@p.com" "ma@pe.com"}))))

    ;; (is (= user-data-set (execute (bind s-parameterized-nil nil))))
    ;; 'null' parameters are not allowed since CQL3 does not (yet) supports them (see https://issues.apache.org/jira/browse/CASSANDRA-3783)
))


(deftest test-lazy-query
  (is (= 10 (count (take 10 (lazy-query
                             (select :items
                                     (limit 2)
                                     (where {:si (int 0)}))
                             (fn [q coll]
                               (merge q (where {:si (-> coll last :si inc)})))
                             :keywordize? true)))))

  (is (= 4 (count (take 10 (lazy-query
                            (select :items
                                    (limit 2)
                                    (where {:si (int 0)}))
                            (fn [q coll]
                              (when (< (-> coll last :si) 3)
                                (merge q (where {:si (-> coll last :si inc)}))))
                            :keywordize? true))))))

;; (run-tests)
