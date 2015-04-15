(ns qbits.alia.codec.nippy
  "Codec that adds encoding support for nippy serialized data. Both
  encoding/decoding are optional. You need to add a nippy dependency
  manually."
  (:require
   [qbits.alia.codec :as codec]
   [taoensso.nippy :as nippy]))

(defn set-nippy-collection-encoder!
  "Forces the encoding of *all* IPersistentCollections (non nil) via
  nippy, this might break prepared statements with cassandra
  collections. For more fine grained control you can use serializable!
  and it's encoder"
  [opts]
  (extend-protocol codec/PCodec
    clojure.lang.IPersistentCollection
    (encode [coll]
      (nippy/freeze coll opts))))

(defn set-nippy-decoder!
  "Forces the decoding of ByteBuffers returned by cassandra via nippy
  to IPersistentCollections. Takes a map or nippy options to be passed
  to thaw at decoding time."
  [opts]
  (extend-protocol codec/PCodec
    java.nio.ByteBuffer
    (encode [x] x)
    (decode [bb]
      (nippy/thaw bb opts))))

(defn set-nippy-serializable-encoder!
  "Sets a nippy decoder for all bytebuffers returned by cassandra.
Encoding is manual, and only applied to values that are marked by
calling serializable! on them. Takes a map or nippy options to be passed
  to thaw/freeze."
  [opts]
  (extend-protocol codec/PCodec
    java.nio.ByteBuffer
    (encode [x] x)
    (decode [bb]
      (nippy/thaw bb opts))
    Object
    (encode [x]
      (if (some-> x meta ::serializable)
        (nippy/freeze x opts)
        x))
    (decode [x] x)))

(defn serializable!
  "Mark value as nippy serializable"
  [coll]
  (some-> coll (alter-meta! assoc ::serializable true)))
