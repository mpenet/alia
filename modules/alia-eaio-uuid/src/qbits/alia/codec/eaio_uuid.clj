(ns qbits.alia.codec.eaio-uuid
  "Codec that adds encoding support for com.eaio.uuid.UUID instances"
  (:require
   [qbits.alia.codec.default :as codec]))

(extend-protocol codec/Encoder
  com.eaio.uuid.UUID
  (encode [x]
    (java.util.UUID/fromString (str x))))
