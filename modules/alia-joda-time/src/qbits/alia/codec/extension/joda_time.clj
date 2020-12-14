(ns qbits.alia.codec.extension.joda-time
  "Codec that adds encoding support for Joda-Time instances"
  (:require
   [qbits.alia.codec.default :as codec]))

;; org.joda.time.DateTime

(defn ^java.time.Instant encode-joda-time-datetime
  [^org.joda.time.DateTime x]
  (java.time.Instant/ofEpochMilli (.getMillis x)))

(extend-protocol codec/Encoder
  org.joda.time.DateTime
  (encode [x]
    (encode-joda-time-datetime x)))

(defn ^org.joda.time.DateTime decode-joda-time-datetime
  [^java.time.Instant x]
  (org.joda.time.DateTime. (.toEpochMilli x)))

(extend-protocol codec/Decoder
  java.time.Instant
  (decode [x]
    (decode-joda-time-datetime x)))

;; org.joda.time.LocalDate

(defn ^java.time.LocalTime encode-joda-local-date
  [^org.joda.time.LocalDate x]
  (java.time.LocalDate/of
   (.getYear x)
   (.getMonthOfYear x)
   (.getDayOfMonth x)))

(extend-protocol codec/Encoder
  org.joda.time.LocalDate
  (encode [x]
    (encode-joda-local-date x)))

(defn ^org.joda.time.LocalDate decode-joda-local-date
  [^java.time.LocalDate x]
  (org.joda.time.LocalDate.
   (.getYear x)
   (.getMonthValue x)
   (.getDayOfMonth x)))

(extend-protocol codec/Decoder
  java.time.LocalDate
  (decode [x]
    (decode-joda-local-date x)))

;; org.joda.time.LocalTime

(defn ^java.time.LocalTime encode-joda-local-time
  [^org.joda.time.LocalTime x]
  (java.time.LocalTime/of (.getHourOfDay x) (.getMinuteOfHour x)))

(extend-protocol codec/Encoder
  org.joda.time.LocalTime
  (encode [x]
    (encode-joda-local-time x)))

(defn ^org.joda.time.LocalTime decode-joda-local-time
  [^java.time.LocalTime x]
  (org.joda.time.LocalTime (.getHour x) (.getMinute x)))

(extend-protocol codec/Decoder
  java.time.LocalTime
  (decode [x]
    (decode-joda-local-time x)))
