(ns qbits.alia.tuple
  (:require
   [qbits.alia.metadata :as md])
  (:import
   [com.datastax.oss.driver.api.core CqlIdentifier]
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core.type
    DataType
    DataTypes
    TupleType
    MapType
    SetType
    ListType]
   [com.datastax.oss.driver.api.core.data TupleValue UdtValue]
   [com.datastax.oss.driver.api.core.metadata Metadata]
   [com.datastax.oss.driver.api.core.metadata.schema
    KeyspaceMetadata
    TableMetadata
    ColumnMetadata]
   [java.util UUID List Map Set]
   [java.time Instant LocalDate LocalTime Duration]
   [java.net InetAddress]
   [java.nio ByteBuffer]))

(defprotocol Encoder
  (-set-field!
    [x tv i]
    [x tv i ec]
    [x tv i kc vc]))

(extend-protocol Encoder

  BigInteger
  (-set-field! [b tv i]
    (.setBigInteger ^TupleValue tv i b))

  Boolean
  (-set-field! [b tv i]
    (.setBoolean ^TupleValue tv i b))

  ByteBuffer
  (-set-field! [b tv i]
    (.setByteBuffer ^TupleValue tv i b))

  LocalDate
  (-set-field! [d tv i]
    (.setLocalDate ^TupleValue tv i d))

  LocalTime
  (-set-field! [t tv i]
    (.setLocalTime ^TupleValue tv i t))

  Instant
  (-set-field! [inst tv i]
    (.setInstant ^TupleValue tv i inst))

  BigDecimal
  (-set-field! [d tv i]
    (.setBigDecimal ^TupleValue tv i d))

  Double
  (-set-field! [d tv i]
    (.setDouble ^TupleValue tv i d))

  Float
  (-set-field! [f tv i]
    (.setFloat ^TupleValue tv i f))

  InetAddress
  (-set-field! [x tv i]
    (.setInetAddress ^TupleValue tv i x))

  Integer
  (-set-field! [x tv i]
    (.setInt ^TupleValue tv i x))

  List
  (-set-field! [l tv i ec]
    (.setList ^TupleValue tv i l ^Class ec))

  Long
  (-set-field! [l tv i]
    (.setLong ^TupleValue tv i l))

  Map
  (-set-field! [m tv i kc vc]
    (.setMap ^TupleValue tv i m ^Class kc ^Class vc))

  Set
  (-set-field! [s tv i ec]
    (.setSet ^TupleValue tv i s ^Class ec))

  String
  (-set-field! [x tv i]
    (.setString ^TupleValue tv i x))

  nil
  (-set-field! [n tv i]
    (.setToNull ^TupleValue tv i))

  String
  (-set-field! [x tv i]
    (.setString ^TupleValue tv i x))

  TupleValue
  (-set-field! [t tv i]
    (.setTupleValue ^TupleValue tv i t))

  UdtValue
  (-set-field! [x tv i]
    (.setUdtValue ^TupleValue tv i x))

  UUID
  (-set-field! [uuid tv i]
    (.setUuid ^TupleValue tv i uuid)))

(defn set-field!
  "Where's flip when you need it"
  [ct tv i x]
  (cond
    (map? x)
    (let [[kc vc] (->> x
                       first
                       (map #(some-> ^Object % .getClass)))]
      (when (not (instance? MapType ct))
        (throw (ex-info "not a map!" ct x)))
      (-set-field!
       x
       tv
       i
       (md/default-class (.getKeyType ^MapType ct)  kc)
       (md/default-class (.getValueType ^MapType ct) vc)))

    (sequential? x)
    (let [ec (some->> x  first (#(.getClass ^Object %)))]
      (when (not (instance? ListType ct))
        (throw (ex-info "not a list!" ct x)))
      (-set-field! x tv i (md/default-class (.getElementType ^ListType ct) ec)))

    (set? x)
    (let [ec (some->> x  first (#(.getClass ^Object %)))]
      (when (not (instance? SetType ct))
        (throw (ex-info "not a set!" ct x)))
      (-set-field! x tv i (md/default-class (.getElementType ^SetType ct) ec)))

    :else
    (-set-field! x tv i)))

(defn encoder
  "Takes a Session, optionally keyspace name, table name and column
  name and returns a function that can be used to encode a collection into
  a TupleValue suitable to be used in PreparedStatements

  TODO broken for collection components - java-driver-4 changed the API"
  ([^Session session table column codec]
   (encoder session nil table column codec))
  ([^Session session ks table column codec]
   (let [^KeyspaceMetadata ksm (md/get-keyspace-metadata session ks)
         ^DataType dt (md/get-column-type session ks table column)

         ^TupleType tuple-type (when (instance? TupleType dt)
                                 dt)]

     (when (nil? tuple-type)
       (throw (ex-info
               "Tuple column not found"
               {:type ::tuple-not-found
                :keyspace (-> ksm .getName .asInternal keyword)
                :table table
                :column column})))

     (let [component-types (.getComponentTypes tuple-type)
           encode (:encoder codec)]

       (fn [coll]
         (let [ttv (.newValue tuple-type)]
           (dorun
            (map-indexed
             (fn [i x]
               (set-field! (.get component-types i) ttv i (encode x)))
             coll))
           ttv))))))
