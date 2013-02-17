(ns qbits.alia.codec.joda-time
  (:require [qbits.alia.codec :as codec]))

(extend-protocol codec/PCodec
  org.joda.time.DateTime
  (encode [x]
    (.toDate x)))