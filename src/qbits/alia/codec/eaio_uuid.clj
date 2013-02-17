(ns qbits.alia.codec.eaio-uuid
  (:require [qbits.alia.codec :as codec]))

(extend-protocol codec/PCodec
  com.eaio.uuid.UUID
  (encode [x]
    (java.util.UUID/fromString (str x))))