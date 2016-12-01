(ns qbits.alia.codec.extension.joda-time
  "Codec that adds encoding support for org.joda.time.DateTime instances"
  (:require
   [qbits.alia.codec.default :as codec]
   [clj-time.coerce :as ct]))

(extend-protocol codec/Encoder
  org.joda.time.DateTime
  (encode [x]
    (.toDate x)))

(extend-protocol codec/Decoder
  java.util.Date
  (decode [x]
    (ct/to-date-time x)))
