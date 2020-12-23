(ns qbits.alia.codec.extension.joda-time-test
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [qbits.alia.codec.extension.joda-time :as sut]))

(deftest encode-joda-time-datetime-test
  (let [dt (org.joda.time.DateTime/now)
        i (sut/encode-joda-time-datetime dt)]
    (is (instance? java.time.Instant i))
    (is (= (.toEpochMilli i)
           (.getMillis dt)))))

(deftest decode-joda-time-datetime-test
  (let [i (java.time.Instant/now)
        dt (sut/decode-joda-time-datetime i)]
    (is (instance? org.joda.time.DateTime dt))
    (is (= (.toEpochMilli i)
           (.getMillis dt)))))

(deftest encode-joda-local-date-test
  (let [joda-ld (org.joda.time.LocalDate. 2020 12 15)
        ^java.time.LocalDate r (sut/encode-joda-local-date joda-ld)]
    (is (instance? java.time.LocalDate r))
    (is (= (.getYear joda-ld)
           (.getYear r)))
    (is (= (.getMonthOfYear joda-ld)
           (.getMonthValue r)))
    (is (= (.getDayOfMonth joda-ld)
           (.getDayOfMonth r)))))

(deftest decode-joda-local-date-test
  (let [java-ld (java.time.LocalDate/of 2020 12 15)
        ^org.joda.time.LocalDate r (sut/decode-joda-local-date java-ld)]
    (is (instance? org.joda.time.LocalDate r))
    (is (= (.getYear java-ld)
           (.getYear r)))
    (is (= (.getMonthValue java-ld)
           (.getMonthOfYear r)))
    (is (= (.getDayOfMonth java-ld)
           (.getDayOfMonth r)))))

(deftest encode-joda-local-time-test
  (let [joda-lt (org.joda.time.LocalTime. 23 37)
        ^java.time.LocalTime r (sut/encode-joda-local-time joda-lt)]
    (is (instance? java.time.LocalTime r))
    (is (= (.getHourOfDay joda-lt)
           (.getHour r)))
    (is (= (.getMinuteOfHour joda-lt)
           (.getMinute r)))))

(deftest decode-joda-local-time-test
  (let [java-lt (java.time.LocalTime/of 23 37)
        ^org.joda.time.LocalTime r (sut/decode-joda-local-time java-lt)]
    (is (instance? org.joda.time.LocalTime r))
    (is (= (.getHour java-lt)
           (.getHourOfDay r)))
    (is (= (.getMinute java-lt)
           (.getMinuteOfHour r)))))
