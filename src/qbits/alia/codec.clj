(ns qbits.alia.codec
  (:import
   [com.datastax.driver.core
    DataType
    DataType$Name
    Row]))

(defmacro make-decoders [row idx col-type & specs]
  (reduce (fn [m [decoder-type# form#]]
            (assoc m
              decoder-type#
              `(fn [~(vary-meta row assoc :tag "com.datastax.driver.core.Row")
                    ~(vary-meta idx assoc :tag "java.lang.Integer")
                    ~(vary-meta col-type assoc :tag "com.datastax.driver.core.DataType")]
                 ~form#)))
          {}
          (partition 2 specs)))

(defn ^Class types-args->type
  [^"[Lcom.datastax.driver.core.DataType;" type-args pred]
  (.asJavaClass ^DataType (pred type-args)))

(def decoders
  (make-decoders row idx col-type
   DataType$Name/ASCII     (.getString row idx)
   DataType$Name/BIGINT    (.getLong row idx)
   DataType$Name/BLOB      (.getBytes row idx)
   DataType$Name/BOOLEAN   (.getBool row idx)
   DataType$Name/COUNTER   (.getLong row idx)
   DataType$Name/DECIMAL   (.getDecimal row idx)
   DataType$Name/DOUBLE    (.getDouble row idx)
   DataType$Name/FLOAT     (.getFloat row idx)
   DataType$Name/INET      (.getInet row idx)
   DataType$Name/INT       (.getInt row idx)
   DataType$Name/TEXT      (.getString row idx)
   DataType$Name/TIMESTAMP (.getDate row idx)
   DataType$Name/TIMEUUID  (.getUUID row idx)
   DataType$Name/UUID      (.getUUID row idx)
   DataType$Name/VARCHAR   (.getString row idx)
   DataType$Name/VARINT    (.getVarint row idx)
   DataType$Name/LIST      (seq (.getList row idx (types-args->type (.getTypeArguments col-type) first)))
   DataType$Name/SET       (into #{} (.getSet row idx (types-args->type (.getTypeArguments col-type) first)))
   DataType$Name/MAP       (let [t (.getTypeArguments ^DataType col-type)]
                             (into {} (.getMap row idx
                                               (types-args->type t first)
                                               (types-args->type t second))))))

(defn decode
  [^Row row ^Integer idx ^DataType col-type]
  ((decoders (.getName col-type)) row idx col-type))

;; only used for prepared statements
(defprotocol PCodec
  (encode [x]
    "Encodes clj value into a valid cassandra value for prepared
    statements (usefull for external libs such as joda time)"))

(extend-protocol PCodec

  clojure.lang.Keyword
  (encode [x] (name x))

  Object
  (encode [x] x)

  nil
  (encode [x]
    (throw (UnsupportedOperationException.
            "'null' parameters are not allowed since CQL3 does
not (yet) supports them. See
https://issues.apache.org/jira/browse/CASSANDRA-3783"))))
