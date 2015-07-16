(ns qbits.alia.codec
  (:require [qbits.commons :refer [case-enum]])
  (:import
    (java.nio ByteBuffer)
    (com.datastax.driver.core
      DataType
      DataType$Name
      GettableByIndexData
      ResultSet
      Row
      UserType$Field
      SettableByNameData
      UDTValue
      TupleValue)
    (java.util UUID List Map Set Date)
    (java.net InetAddress)))

(defprotocol PCodec
  (decode [x]
    "Decodes raw deserialzed values returned by java-driver into clj
  friendly types")
  (encode [x]
    "Encodes clj value into a valid cassandra value for prepared
    statements (usefull for external libs such as joda time)"))

(defprotocol PNamedBinding
  "Bind the val onto Settable by name"
  (set-by-name [val settable name]))

(declare deserialize)

(defmacro defdeserializers [[x idx col-type] & specs]
  (let [args [(vary-meta x assoc :tag "com.datastax.driver.core.GettableByIndexData")
              (vary-meta idx assoc :tag "java.lang.Integer")
              (vary-meta col-type assoc :tag "com.datastax.driver.core.DataType")]]
    `(do
       ~@(conj
          (mapv (fn [[decoder-type form]]
                  `(defn ~(->> decoder-type name (str "decode-") symbol)
                     ~args
                     ~form))
                (partition 2 specs))
          `(defn deserialize ~args
             (case-enum (-> ~col-type .getName)
               ~@(mapcat (fn [[decoder-type _]]
                           [(->> (name decoder-type)
                                 (.toUpperCase)
                                 (str "com.datastax.driver.core.DataType$Name/")
                                 symbol)
                            `(~(symbol (str "decode-" (name decoder-type)))
                              ~x ~idx ~col-type)])
                         (partition 2 specs))))))))

(defn ^Class types-args->type
  [^"[Lcom.datastax.driver.core.DataType;" type-args pred]
  (.asJavaClass ^DataType (pred type-args)))

(defdeserializers [x idx col-type]
 :ascii     (decode (.getString x idx))
 :bigint    (decode (.getLong x idx))
 :blob      (decode (.getBytes x idx))
 :boolean   (decode (.getBool x idx))
 :counter   (decode (.getLong x idx))
 :custom    (decode (.getBytesUnsafe x idx))
 :decimal   (decode (.getDecimal x idx))
 :double    (decode (.getDouble x idx))
 :float     (decode (.getFloat x idx))
 :inet      (decode (.getInet x idx))
 :int       (decode (.getInt x idx))
 :text      (decode (.getString x idx))
 :timestamp (decode (.getDate x idx))
 :timeuuid  (decode (.getUUID x idx))
 :uuid      (decode (.getUUID x idx))
 :varchar   (decode (.getString x idx))
 :varint    (decode (.getVarint x idx))
 :list      (decode (.getList x idx (types-args->type (.getTypeArguments col-type) first)))
 :set       (decode (.getSet x idx (types-args->type (.getTypeArguments col-type) first)))
 :map       (let [t (.getTypeArguments col-type)]
              (decode (.getMap x idx
                               (types-args->type t first)
                               (types-args->type t second))))
 :tuple     (let [tuple-value (.getTupleValue x idx)
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
 :udt       (let [udt-value (.getUDTValue x idx)
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
                           (unchecked-inc-int idx')))))))

(extend-protocol PCodec

  (Class/forName "[B")
  (encode [x] (ByteBuffer/wrap x))
  (decode [x] x)

  Map
  (encode [x] x)
  (decode [x] (into {} x))

  Set
  (encode [x] x)
  (decode [x] (into #{} x))

  List
  (encode [x] x)
  (decode [x] (into [] x))

  Object
  (encode [x] x)
  (decode [x] x)

  nil
  (decode [x] x)
  (encode [x] x))

(extend-protocol PNamedBinding
  Boolean
  (set-by-name [val settable name]
    (.setBool ^SettableByNameData settable name val))
  Integer
  (set-by-name [val settable name]
    (.setInt ^SettableByNameData settable name val))
  Long
  (set-by-name [val settable name]
    (.setLong ^SettableByNameData settable name val))
  Date
  (set-by-name [val settable name]
    (.setDate ^SettableByNameData settable name val))
  Float
  (set-by-name [val settable name]
    (.setFloat ^SettableByNameData settable name val))
  Double
  (set-by-name [val settable name]
    (.setDouble ^SettableByNameData settable name val))
  String
  (set-by-name [val settable name]
    (.setString ^SettableByNameData settable name val))
  ByteBuffer
  (set-by-name [val settable name]
    (.setBytes ^SettableByNameData settable name val))
  BigInteger
  (set-by-name [val settable name]
    (.setVarint ^SettableByNameData settable name val))
  BigDecimal
  (set-by-name [val settable name]
    (.setDecimal ^SettableByNameData settable name val))
  UUID
  (set-by-name [val settable name]
    (.setUUID ^SettableByNameData settable name val))
  InetAddress
  (set-by-name [val settable name]
    (.setInet ^SettableByNameData settable name val))
  List
  (set-by-name [val settable name]
    (.setList ^SettableByNameData settable name val))
  Map
  (set-by-name [val settable name]
    (.setMap ^SettableByNameData settable name val))
  Set
  (set-by-name [val settable name]
    (.setSet ^SettableByNameData settable name val))
  UDTValue
  (set-by-name [val settable name]
    (.setUDTValue ^SettableByNameData settable name val))
  TupleValue
  (set-by-name [val settable name]
    (.setTupleValue ^SettableByNameData settable name val))
  nil
  (set-by-name [_ settable name]
    (.setToNull ^SettableByNameData settable name)))

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
