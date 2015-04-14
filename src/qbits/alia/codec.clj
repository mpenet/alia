(ns qbits.alia.codec
  (:import
   (java.nio ByteBuffer)
   (com.datastax.driver.core
    DataType
    DataType$Name
    GettableByIndexData
    ResultSet
    Row
    UserType$Field)))

(defprotocol PCodec
  (decode [x]
    "Decodes raw deserialzed values returned by java-driver into clj
  friendly types")
  (encode [x]
    "Encodes clj value into a valid cassandra value for prepared
    statements (usefull for external libs such as joda time)"))

(defmacro make-deserializers [x idx col-type & specs]
  (let [hm (gensym)]
    `(let [~hm (java.util.HashMap. ~(count specs))]
       ~@(for [[decoder-type form] (partition 2 specs)]
           `(.put ~hm
                  ~decoder-type
                  (fn [~(vary-meta x assoc :tag "com.datastax.driver.core.GettableByIndexData")
                       ~(vary-meta idx assoc :tag "java.lang.Integer")
                       ~(vary-meta col-type assoc :tag "com.datastax.driver.core.DataType")]
                    ~form)))
       ~hm)))

(defn ^Class types-args->type
  [^"[Lcom.datastax.driver.core.DataType;" type-args pred]
  (.asJavaClass ^DataType (pred type-args)))

(declare deserializers)
(defn deserialize
  [^GettableByIndexData x ^Integer idx ^DataType col-type]
  ((.get ^java.util.HashMap deserializers (.getName col-type)) x idx col-type))

(def deserializers
  (make-deserializers x idx col-type
                      DataType$Name/ASCII     (decode (.getString x idx))
                      DataType$Name/BIGINT    (decode (.getLong x idx))
                      DataType$Name/BLOB      (decode (.getBytes x idx))
                      DataType$Name/BOOLEAN   (decode (.getBool x idx))
                      DataType$Name/COUNTER   (decode (.getLong x idx))
                      DataType$Name/CUSTOM    (decode (.getBytesUnsafe x idx))
                      DataType$Name/DECIMAL   (decode (.getDecimal x idx))
                      DataType$Name/DOUBLE    (decode (.getDouble x idx))
                      DataType$Name/FLOAT     (decode (.getFloat x idx))
                      DataType$Name/INET      (decode (.getInet x idx))
                      DataType$Name/INT       (decode (.getInt x idx))
                      DataType$Name/TEXT      (decode (.getString x idx))
                      DataType$Name/TIMESTAMP (decode (.getDate x idx))
                      DataType$Name/TIMEUUID  (decode (.getUUID x idx))
                      DataType$Name/UUID      (decode (.getUUID x idx))
                      DataType$Name/VARCHAR   (decode (.getString x idx))
                      DataType$Name/VARINT    (decode (.getVarint x idx))
                      DataType$Name/LIST      (decode (.getList x idx (types-args->type (.getTypeArguments col-type) first)))
                      DataType$Name/SET       (decode (.getSet x idx (types-args->type (.getTypeArguments col-type) first)))
                      DataType$Name/MAP       (let [t (.getTypeArguments col-type)]
                                                (decode (.getMap x idx
                                                                 (types-args->type t first)
                                                                 (types-args->type t second))))
                      DataType$Name/TUPLE     (let [tuple-value (.getTupleValue x idx)
                                                    types (.getComponentTypes (.getType tuple-value))
                                                    len (.size types)]
                                                (loop [tuple []
                                                       idx' 0]
                                                  (if (= idx' len)
                                                    tuple
                                                    (recur (conj tuple (decode (deserialize tuple-value
                                                                                            idx'
                                                                                            (.get types idx'))))
                                                           (unchecked-inc-int idx')))))
                      DataType$Name/UDT       (let [udt-value (.getUDTValue x idx)
                                                    udt-type (.getType udt-value)
                                                    udt-type-iter (.iterator udt-type)
                                                    len (.size udt-type)]
                                                (loop [udt {}
                                                       idx' 0]
                                                  (if (= idx' len)
                                                    udt
                                                    (let [^UserType$Field type (.next udt-type-iter)]
                                                      (recur (assoc udt
                                                                    (.getName type)
                                                                    (decode (deserialize udt-value
                                                                                         idx'
                                                                                         (.getType type))))
                                                             (unchecked-inc-int idx'))))))))

(extend-protocol PCodec

  (Class/forName "[B")
  (encode [x] (ByteBuffer/wrap x))
  (decode [x] x)

  java.util.Map
  (encode [x] x)
  (decode [x] (into {} x))

  java.util.Set
  (encode [x] x)
  (decode [x] (into #{} x))

  java.util.List
  (encode [x] x)
  (decode [x] (into [] x))

  Object
  (encode [x] x)
  (decode [x] x)

  nil
  (decode [x] x)
  (encode [x] x))

(defn result-set->maps
  [^ResultSet result-set string-keys?]
  (let [key-fn (if string-keys? identity keyword)]
    (-> (map (fn [^Row row]
               (let [cdef (.getColumnDefinitions row)
                     len (.size cdef)]
                 (loop [idx (int 0)
                        row-map (transient {})]
                   (if (= idx len)
                     (persistent! row-map)
                     (recur (unchecked-inc-int idx)
                            (assoc! row-map
                                    (key-fn (.getName cdef idx))
                                    (deserialize row idx (.getType cdef idx))))))))
             result-set)
        (vary-meta assoc :execution-info (.getExecutionInfo result-set)))))
