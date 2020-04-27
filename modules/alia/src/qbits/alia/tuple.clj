(ns qbits.alia.tuple
  (:import
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core.type DataType TupleType]
   [com.datastax.oss.driver.api.core.data TupleValue UdtValue]
   [com.datastax.oss.driver.api.core.metadata.schema
    KeyspaceMetadata
    TableMetadata
    ColumnMetadata]
   [java.util UUID List Map Set]
   [java.time Instant LocalDate LocalTime]
   [java.net InetAddress]
   [java.nio ByteBuffer]))

(defprotocol Encoder
  (-set-field!
    [x u i]
    [x u i ec]
    [x u i kc vc]))

(extend-protocol Encoder

  BigInteger
  (-set-field! [b u i]
    (.setBigInteger ^TupleValue u i b))

  Boolean
  (-set-field! [b u i]
    (.setBoolean ^TupleValue u i b))

  ByteBuffer
  (-set-field! [b u i]
    (.setByteBuffer ^TupleValue u i b))

  LocalDate
  (-set-field! [d u i]
    (.setLocalDate ^TupleValue u i d))

  LocalTime
  (-set-field! [t u i]
    (.setLocalTime ^TupleValue u i t))

  Instant
  (-set-field! [inst u i]
    (.setInstant ^TupleValue u i inst))

  BigDecimal
  (-set-field! [d u i]
    (.setBigDecimal ^TupleValue u i d))

  Double
  (-set-field! [d u i]
    (.setDouble ^TupleValue u i d))

  Float
  (-set-field! [f u i]
    (.setFloat ^TupleValue u i f))

  InetAddress
  (-set-field! [x u i]
    (.setInetAddress ^TupleValue u i x))

  Integer
  (-set-field! [x u i]
    (.setInt ^TupleValue u i x))

  List
  (-set-field! [l u i ec]
    (.setList ^TupleValue u i l ^Class ec))

  Long
  (-set-field! [l u i]
    (.setLong ^TupleValue u i l))

  Map
  (-set-field! [m u i kc vc]
    (.setMap ^TupleValue u i m ^Class kc ^Class vc))

  Set
  (-set-field! [s u i ec]
    (.setSet ^TupleValue u i s ^Class ec))

  String
  (-set-field! [x u i]
    (.setString ^TupleValue u i x))

  nil
  (-set-field! [n u i]
    (.setToNull ^TupleValue u i))

  String
  (-set-field! [x u i]
    (.setString ^TupleValue u i x))

  TupleValue
  (-set-field! [t u i]
    (.setTupleValue ^TupleValue u i t))

  UdtValue
  (-set-field! [x u i]
    (.setUdtValue ^TupleValue u i x))

  UUID
  (-set-field! [uuid u i]
    (.setUuid ^TupleValue u i uuid)))

(defn set-field!
  "Where's flip when you need it"
  ([u k x]
   (-set-field! x u k))
  ([u k x ec]
   (-set-field! x u k ec))
  ([u k x kc vc]
   (-set-field! x u k kc vc)))

(defn encoder
  "Takes a Session, optionaly keyspace name, table name and column
  name and returns a function that can be used to encode a collection into
  a TupleValue suitable to be used in PreparedStatements

  TODO broken for collection values - java-driver-4 changed the API"
  ([^Session session table column codec]
   (encoder session (.getKeyspace session) table column codec))
  ([^Session session ks table column codec]
   (let [^KeyspaceMetadata ks-metadata
         (some-> session
                 .getMetadata
                 (.getKeyspace (name (or ks (.getKeyspace session))))
                 (.get))

         ^TableMetadata table-metadata
         (some-> ks-metadata
                 (.getTable (name table))
                 (.get))

         ^ColumnMetadata column-metadata
         (some-> table-metadata
                 (.getColumn (name column))
                 (.get))


         ^DataType dt
         (some-> column-metadata
                 (.getType))

         ^TupleType t (when (instance? TupleType dt)
                        dt)

         encode (:encoder codec)]
     (when-not t
       (throw (ex-info (format "Tuple Column '%s' not found on Keyspace '%s'"
                               (name column)
                               (name ks))
                       {:type ::type-not-found})))
     (fn [coll]
       (let [ttv (.newValue t)]
         (loop [i 0
                coll coll]
           (if-let [x (first coll)]
             (set-field! ttv i (encode x))
             (recur (inc i) (rest coll))))
         ttv)))))
