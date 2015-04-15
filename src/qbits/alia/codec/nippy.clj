(ns qbits.alia.codec.nippy
  "Codec that adds encoding support for nippy serialized data. Both
  encoding/decoding are optional. You need to add a nippy dependency
  manually. It only works for prepared statements (write time)"
  (:require
   [qbits.alia.codec :as codec]
   [taoensso.nippy :as nippy]))

(defn set-nippy-collection-encoder!
  "Forces the encoding of *all* IPersistentCollections (non nil) via
  nippy, this might break prepared statements with cassandra
  collections. For more fine grained control you can use serializable!
  and it's encoder"
  ([] (set-nippy-collection-encoder! nil))
  ([opts]
   (extend-protocol codec/PCodec
     clojure.lang.IPersistentCollection
     (encode [coll]
       (nippy/freeze coll opts)))))

(defn set-nippy-decoder!
  "Forces the decoding of ByteBuffers returned by cassandra via nippy
  to IPersistentCollections. Takes a map or nippy options to be passed
  to thaw at decoding time."
  ([] (set-nippy-decoder! nil))
  ([opts]
   (extend-protocol codec/PCodec
     java.nio.ByteBuffer
     (encode [x] x)
     (decode [bb]
       (nippy/thaw bb opts)))))

(deftype NippySerializable [data])

(defn set-nippy-serializable-encoder!
  "Sets a nippy decoder for all bytebuffers returned by cassandra.
Encoding is manual, and only applied to values that are marked by
calling serializable! on them. Takes a map or nippy options to be passed
  to thaw/freeze."
  ([] (set-nippy-serializable-encoder! nil))
  ([opts]
   (extend-protocol codec/PCodec
     java.nio.ByteBuffer
     (encode [x] x)
     (decode [bb]
       (nippy/thaw bb opts))

     NippySerializable
     (encode [x]
       (nippy/freeze (.data x) opts)))))

(defn serializable!
  "Mark value as nippy serializable"
  [coll]
  (NippySerializable. coll))
