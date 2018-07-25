(ns qbits.alia.codec.extension.joda-time
  "Codec that adds encoding support for Joda-Time instances"
  (:require
   [qbits.alia.codec.default :as codec]
   [clj-time.coerce :as ct]))

;; org.joda.time.DateTime
(extend-protocol codec/Encoder
  org.joda.time.DateTime
  (encode [x]
    (.toDate x)))

(extend-protocol codec/Decoder
  java.util.Date
  (decode [x]
    (ct/to-date-time x)))

;; org.joda.time.LocalDate
(extend-protocol codec/Decoder
  com.datastax.driver.core.LocalDate
  (decode [x]
    (new org.joda.time.LocalDate (.getMillisSinceEpoch x))))

(extend-protocol codec/Encoder
  org.joda.time.LocalDate
  (encode [x]
    (com.datastax.driver.core.LocalDate/fromYearMonthDay
     (.getYear x) (.getMonthOfYear x) (.getDayOfMonth x))))

(extend-protocol qbits.alia.codec/PNamedBinding
  com.datastax.driver.core.LocalDate
  (-set-named-parameter! [val settable name]
    (.setDate ^com.datastax.driver.core.SettableByNameData settable name val)))
