(ns qbits.alia.codec.joda-time
  "Codec that adds encoding support for org.joda.time.DateTime instances"
  (:require
   [qbits.alia.codec :as codec]
   [clj-time.coerce :as ct]))

(extend-protocol codec/PCodec
  org.joda.time.DateTime
  (encode [x]
    (ct/to-long x)))
