(ns qbits.alia.core-test
  (:use clojure.test
        qbits.alia))

(def ^:dynamic *cluster* )
(def ^:dynamic *session*)

(use-fixtures
  :once
  (fn [test-runner]
    (let [cl (cluster "127.0.0.1" :port 9042)]
    (binding [*session* (connect cl "demodb2")]
      ;; (execute *session* "CREATE KEYSPACE alia-tests
      ;;    WITH strategy_class = 'SimpleStrategy'
      ;;    AND strategy_options:replication_factor='1';")
      ;; (execute *session* "CREATE TABLE users (
      ;;           user_name varchar,
      ;;           password varchar,
      ;;           gender varchar,
      ;;           session_token varchar,
      ;;           state varchar,
      ;;           birth_year bigint,
      ;;           emails set<text>
      ;;           PRIMARY KEY (user_name)
      ;;         );")
      ;; (execute *session* "INSERT INTO users (user_id, first_name, last_name, emails)
      ;;  VALUES('frodo', 'Frodo', 'Baggins', {'f@baggins.com', 'baggins@gmail.com'});")
      (test-runner))
    ;; (shutdown *session*)
    ;; (shutdown cluster)
    )))

(deftest test-sync-execute
  (is (= 0 (execute *session* "select * from users;"
                    ;; :callback (fn [r]
                    ;;             (prn r))
                    ))))