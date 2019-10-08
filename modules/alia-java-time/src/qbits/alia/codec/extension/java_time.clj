(ns qbits.alia.codec.extension.joda-time
  "Codec that adds encoding support for java.time instances"
  (:require
   [qbits.alia.codec.default :as codec]))

(extend-protocol codec/Encoder
  java.time.Instant
  (encode [i]
    (java.util.Date/from i)))

(extend-protocol codec/Decoder
  java.util.Date
  (decode [d]
    (.toInstant d)))
