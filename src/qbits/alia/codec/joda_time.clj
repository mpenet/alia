(ns qbits.alia.codec.joda-time
  (:require
   [qbits.alia.codec :as codec]
   [clj-time.coerce :as ct]))

(extend-protocol codec/PCodec
  org.joda.time.DateTime
  (encode [x]
    (ct/to-long x)))
