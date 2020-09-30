(ns qbits.alia.udt
  (:require
   [qbits.alia.metadata :as md])
  (:import
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core.type
    DataType
    UserDefinedType
    MapType
    SetType
    ListType]
   [com.datastax.oss.driver.api.core.data TupleValue UdtValue]
   [com.datastax.oss.driver.api.core.metadata.schema KeyspaceMetadata]
   [java.util UUID List Map Set]
   [java.time Instant LocalDate LocalTime]
   [java.net InetAddress]
   [java.nio ByteBuffer]))

(defprotocol Encoder
  (-set-field!
    [x u k]
    [x u k ec]
    [x u k kc vc]))

(extend-protocol Encoder

  BigInteger
  (-set-field! [b u k]
    (.setBigInteger ^UdtValue u ^String k b))

  Boolean
  (-set-field! [b u k]
    (.setBoolean ^UdtValue u ^String k  b))

  ByteBuffer
  (-set-field! [b u k]
    (.setByteBuffer ^UdtValue u ^String k  b))

  LocalDate
  (-set-field! [d u k]
    (.setLocalDate ^UdtValue u ^String k  d))

  LocalTime
  (-set-field! [t u k]
    (.setLocalTime ^UdtValue u ^String k t))

  Instant
  (-set-field! [i u k]
    (.setInstant ^UdtValue u ^String k i))

  BigDecimal
  (-set-field! [d u k]
    (.setBigDecimal ^UdtValue u ^String k  d))

  Double
  (-set-field! [d u k]
    (.setDouble ^UdtValue u ^String k  d))

  Float
  (-set-field! [f u k]
    (.setFloat ^UdtValue u ^String k  f))

  InetAddress
  (-set-field! [i u k]
    (.setInetAddress ^UdtValue u ^String k  i))

  Integer
  (-set-field! [i u k]
    (.setInt ^UdtValue u ^String k  i))

  List
  (-set-field! [l u k ec]
    (.setList ^UdtValue u ^String k  l ^Class ec))

  Long
  (-set-field! [l u k]
    (.setLong ^UdtValue u ^String k  l))

  Map
  (-set-field! [m u k kc vc]
    (.setMap ^UdtValue u ^String k  m ^Class kc ^Class vc))

  Set
  (-set-field! [s u k ec]
    (.setSet ^UdtValue u ^String k  s ^Class ec))

  String
  (-set-field! [x u k]
    (.setString ^UdtValue u ^String k  x))

  nil
  (-set-field! [n u k]
    (.setToNull ^UdtValue u ^String k))

  String
  (-set-field! [x u k]
    (.setString ^UdtValue u ^String k  x))

  TupleValue
  (-set-field! [t u k]
    (.setTupleValue ^UdtValue u ^String k  t))

  UdtValue
  (-set-field! [x u k]
    (.setUdtValue ^UdtValue u ^String k  x))

  UUID
  (-set-field! [uuid u k]
    (.setUuid ^UdtValue u ^String k  uuid)))

(defn set-field!
  "Where's flip when you need it"
  [ct utv k x]
  (cond
    (map? x)
    (let [[kc vc] (->> x
                       first
                       (map #(some-> ^Object % .getClass)))]
      (when (not (instance? MapType ct))
        (throw (ex-info "not a map!" ct x)))
      (-set-field!
       x
       utv
       k
       (md/default-class (.getKeyType ^MapType ct)  kc)
       (md/default-class (.getValueType ^MapType ct) vc)))

    (sequential? x)
    (let [ec (some->> x  first #(.getClass ^Object %))]
      (when (not (instance? ListType ct))
        (throw (ex-info "not a list!" ct x)))
      (-set-field! x utv k (md/default-class (.getElementType ^ListType ct) ec)))

    (set? x)
    (let [ec (some->> x  first #(.getClass ^Object %))]
      (when (not (instance? SetType ct))
        (throw (ex-info "not a set!" ct x)))
      (-set-field! x utv k (md/default-class (.getElementType ^SetType ct) ec)))

    :else
    (-set-field! x utv k)))

(defn encoder
  "Takes a Session, optionally keyspace name, UDT name and returns a
  function that can be used to encode a map into a UdtValue suitable
  to be used in PreparedStatements

  TODO broken for collection values - java-driver-4 changed the API"
  ([^Session session typename codec]
   (encoder session nil typename codec))
  ([^Session session ks typename codec]
   (let [^KeyspaceMetadata ksm (md/get-keyspace-metadata session ks)
         ^UserDefinedType t (md/get-udt-metadata session ks typename)]

     (when-not t
       (throw (ex-info
               "UDT not found"
               {:type ::udt-not-found
                :keyspace (-> ksm .getName .asInternal)
                :udt typename})))

     (let [field-names (->> (.getFieldNames t)
                            (map md/cql-id->kw))
           field-types (.getFieldTypes t)

           types-by-name (into {} (map vector field-names field-types))

           encode (:encoder codec)]
       (fn [x]
         (let [utv (.newValue t)]
           (doseq [[k v] x]
             (set-field!
              (get types-by-name (keyword k))
              utv
              (name k)
              (encode v)))
           utv))))))
