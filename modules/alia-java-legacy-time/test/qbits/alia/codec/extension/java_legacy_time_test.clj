(ns qbits.alia.codec.extension.java-legacy-time-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [qbits.alia.codec.extension.java-legacy-time :as sut]))


(deftest encode-java-util-date-test
  (let [d (java.util.Date.)
        i (sut/encode-java-util-date d)]
    (is (instance? java.time.Instant i))
    (is (= (.getTime d)
           (.toEpochMilli i)))))

(deftest decode-java-util-date-test
  (let [i (java.time.Instant/now)
        d (sut/decode-java-util-date i)]
    (is (instance? java.util.Date d))
    (is (= (.getTime d)
           (.toEpochMilli i)))))
