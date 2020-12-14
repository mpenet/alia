(ns qbits.alia.codec.extension.java-legacy-time
  "Codec that adds limited encoding support for java legacy times.

   only java.util.Date mappings to timestamp columns is supported"
  (:require
   [qbits.alia.codec.default :as codec]))

(defn ^java.time.Instant encode-java-util-date
  [^java.util.Date d]
  (java.time.Instant/ofEpochMilli (.getTime d)))

(extend-protocol codec/Encoder
  java.util.Date
  (encode [d]
    (encode-java-util-date d)))

(defn ^java.util.Date decode-java-util-date
  [^java.time.Instant i]
  (java.util.Date. (.toEpochMilli i)))

(extend-protocol codec/Decoder
  java.time.Instant
  (decode [i]
    (decode-java-util-date i)))
