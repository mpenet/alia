(ns qbits.alia.codec.joda-time
  "Codec that adds encoding support for org.joda.time.DateTime instances"
  (:require
   [qbits.alia.codec :as codec]
   [clj-time.coerce :as ct]))

(extend-protocol codec/PCodec
  org.joda.time.DateTime
  (encode [x]
    (.toDate x)))

(extend-protocol codec/PCodec
  java.util.Date
  (decode [x]
    (ct/to-date-time x)))
