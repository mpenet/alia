(ns qbits.alia.core-test
  (:use clojure.test
        clojure.data
        qbits.alia))

(def ^:dynamic *cluster*)

;; <native-type> ::= ascii
;;                 | bigint
;;                 | blob
;;                 | boolean
;;                 | counter
;;                 | decimal
;;                 | double
;;                 | float
;;                 | inet
;;                 | int
;;                 | text
;;                 | timestamp
;;                 | timeuuid
;;                 | uuid
;;                 | varchar
;;                 | varint


;; ascii	ASCII character string
;; bigint	64-bit signed long
;; blob	Arbitrary bytes (no validation)
;; boolean	true or false
;; counter	Counter column (64-bit signed value). See Counters for details
;; decimal	Variable-precision decimal
;; double	64-bit IEEE-754 floating point
;; float	32-bit IEEE-754 floating point
;; inet	An IP address. It can be either 4 bytes long (IPv4) or 16 bytes long (IPv6)
;; int	32-bit signed int
;; text	UTF8 encoded string
;; timestamp	A timestamp. See Working with dates below for more information.
;; timeuuid	Type 1 UUID. This is generally used as a “conflict-free” timestamp. See Working with timeuuid below.
;; uuid	Type 1 or type 4 UUID
;; varchar	UTF8 encoded string
;; varint	Arbitrary-precision integer


;; some test data
(def user-data-set [[{:name "user_name", :value "mpenet"}
                     {:name "first_name", :value "Max"}
                     {:name "last_name", :value "Penet"}
                     {:name "emails", :value #{"m@p.com" "ma@pe.com"}}
                     {:name "birth_year", :value 0}
                     {:name "created" :value nil}
                     {:name "valid" :value true}
                     {:name "amap", :value {"foo" 1 "bar" 2}}
                     {:name "tags" :value [1 2 3]}
                     {:name "auuid" :value #uuid "42048d2d-c135-4c18-aa3a-e38a6d3be7f1"}]
                    [{:name "user_name", :value "frodo"}
                     {:name "first_name", :value "Frodo"}
                     {:name "last_name", :value "Baggins"}
                     {:name "birth_year", :value 1}
                     {:name "created" :value nil}
                     {:name "valid" :value true}
                     {:name "emails", :value #{"baggins@gmail.com" "f@baggins.com"}}
                     {:name "amap", :value {"foo" 1 "bar" 2}}
                     {:name "tags" :value [4 5 6]}
                     {:name "auuid" :value #uuid "1f84b56b-5481-4ee4-8236-8a3831ee5892"}]])

;; helpers
(def execute->map (comp rows->maps execute))
(def user-data-set-as-map (rows->maps user-data-set))

(use-fixtures
  :once
  (fn [test-runner]
    ;; prepare the thing
    (binding [*cluster* (cluster "127.0.0.1" :port 9042)]
      (with-session (connect *cluster*)
        (try (execute *session* "DROP KEYSPACE alia;")
             (catch Exception _ nil))
        (execute *session* "CREATE KEYSPACE alia
         WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 3};")
        (execute *session* "USE alia;")
        (execute *session* "CREATE TABLE users (
                user_name varchar,
                first_name varchar,
                last_name varchar,
                auuid uuid,
                birth_year bigint,
                created timestamp,
                valid boolean,
                emails set<text>,
                tags list<bigint>,
                amap map<varchar, bigint>,
                PRIMARY KEY (user_name)
              );")
        (execute *session* "CREATE INDEX ON users (birth_year);")

        (execute *session* "INSERT INTO users (user_name, first_name, last_name, emails, birth_year, amap, tags, auuid,valid)
       VALUES('frodo', 'Frodo', 'Baggins', {'f@baggins.com', 'baggins@gmail.com'}, 1, {'foo': 1, 'bar': 2}, [4, 5, 6], '1f84b56b-5481-4ee4-8236-8a3831ee5892', true);")
        (execute *session* "INSERT INTO users (user_name, first_name, last_name, emails, birth_year, amap, tags, auuid, valid)
       VALUES('mpenet', 'Max', 'Penet', {'m@p.com', 'ma@pe.com'}, 0, {'foo': 1, 'bar': 2}, [1, 2, 3], '42048d2d-c135-4c18-aa3a-e38a6d3be7f1', true);")

        ;; do the thing
        (test-runner)

        ;; destroy the thing
        (try (execute *session* "DROP KEYSPACE alia;") (catch Exception _ nil))
        (shutdown *session*)
        (shutdown *cluster*)))))

(deftest test-sync-execute
  (is (= user-data-set-as-map
         (execute->map *session* "select * from users;"))))

(deftest test-async-execute
  ;; promise
  (is (= user-data-set-as-map
         (rows->maps @(execute *session* "select * from users;" :async? true))))
  ;; callback
  (let [p (promise)]
    (execute *session* "select * from users;"
             :success (fn [r] (deliver p (rows->maps r))))
    (is (= user-data-set-as-map @p))))


(deftest test-prepared
  (let [s-simple (prepare *session* "select * from users;")
        s-parameterized-simple (prepare *session* "select * from users where user_name=?;")
        s-parameterized-bigint (prepare *session* "select * from users where birth_year=?;")
        s-prepare-types (prepare *session* "INSERT INTO users (user_name, auuid, created, valid) VALUES(?, ?, ?, ?);")
        ;; s-parameterized-set (prepare *session* "select * from users where emails=?;")
        ;; s-parameterized-nil (prepare *session* "select * from users where session_token=?;")
        ]
    (is (= user-data-set-as-map (execute->map *session* (bind s-simple))))
    (is (= [(first user-data-set-as-map)]
           (execute->map *session* (bind s-parameterized-simple "mpenet"))))
    (is (= [(first user-data-set-as-map)]
           (execute->map *session* (bind s-parameterized-simple :mpenet))))
    (is (= [(first user-data-set-as-map)]
           (execute->map *session* (bind s-parameterized-bigint 0))))
    (is (= [] (execute *session* (bind s-prepare-types
                                       "foobar"
                                       #uuid "b474e171-7757-449a-87be-d2797d1336e3"
                                       (java.util.Date.)
                                       false))))
    (execute *session* "delete from users where user_name = 'foobar';") ;; cleanup

    ;; ;; index on collections not supp  orted yet
    ;; (is (= [(first user-data-set)]
    ;;        (execute *session* (bind s-parameterized-set #{"m@p.com" "ma@pe.com"}))))

    ;; (is (= user-data-set (execute *session* (bind s-parameterized-nil nil))))
    ;; 'null' parameters are not allowed since CQL3 does not (yet) supports them (see https://issues.apache.org/jira/browse/CASSANDRA-3783)
))

(deftest test-rows-to-map
  (is (= [{1 2, 2 3}]
         (rows->maps [[{:name 1 :value 2} {:name 2 :value 3}]]))))