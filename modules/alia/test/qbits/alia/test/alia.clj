(ns qbits.alia.test.alia
  "Function tests that don't require a running Cassandra instance"
  (:require [clojure.test :refer :all]
            [qbits.alia :refer :all])
  (:import (com.datastax.driver.core ProtocolVersion SimpleStatement)))

(deftest query->statement-test
  (is (nil?
       (-> "use foo;"
           ^SimpleStatement (query->statement nil nil)
           (.getValues ProtocolVersion/V1 nil)))
      "Protocol V1 doesn't support execute with values so values should be nil when not given")

  (is (not (nil? (-> "select * from users where user_name= :name"
                     ^SimpleStatement (query->statement [nil] {:encoder identity})
                     (.getValues nil nil))))))
