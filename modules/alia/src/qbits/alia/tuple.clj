(ns qbits.alia.tuple
  (:import
   (com.datastax.driver.core
    Session
    TupleValue
    UDTValue
    TupleType)
   (java.util
    UUID
    List
    Map
    Set
    Date)
   (java.net InetAddress)
   (java.nio ByteBuffer)))

(defprotocol Encoder
  (-set-field! [x u i]))

(extend-protocol Encoder

  BigInteger
  (-set-field! [b u i]
    (.setVarint ^TupleValue u i b))

  Boolean
  (-set-field! [b u i]
    (.setBool ^TupleValue u i b))

  ByteBuffer
  (-set-field! [b u i]
    (.setBytes ^TupleValue u i b))

  Date
  (-set-field! [d u i]
    (.setDate ^TupleValue u i d))

  BigDecimal
  (-set-field! [d u i]
    (.setDecimal ^TupleValue u i d))

  Double
  (-set-field! [d u i]
    (.setDouble ^TupleValue u i d))

  Float
  (-set-field! [f u i]
    (.setFloat ^TupleValue u i f))

  InetAddress
  (-set-field! [i u i]
    (.setInet ^TupleValue u i i))

  Integer
  (-set-field! [i u i]
    (.setInt ^TupleValue u i i))

  List
  (-set-field! [l u i]
    (.setList ^TupleValue u i l))

  Long
  (-set-field! [l u i]
    (.setLong ^TupleValue u i l))

  Map
  (-set-field! [m u i]
    (.setMap ^TupleValue u i m))

  Set
  (-set-field! [s u i]
    (.setSet ^TupleValue u i s))

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

  UDTValue
  (-set-field! [x u i]
    (.setUDTValue ^TupleValue u i x))

  UUID
  (-set-field! [uuid u i]
    (.setUUID ^TupleValue u i uuid)))

(defn set-field!
  "Where's flip when you need it"
  [u k x]
  (-set-field! x u k))

(defn encoder
  "Takes a Session, optionaly keyspace name, table name and column
  name and returns a function that can be used to encode a collection into
  a TupleValue suitable to be used in PreparedStatements"
  ([^Session session table column codec]
   (encoder session (.getLoggedKeyspace session) table column codec))
  ([^Session session ks table column codec]
   (let [^TupleType t (some-> session
                              .getCluster
                              .getMetadata
                              (.getKeyspace (name ks))
                              (.getTable (name table))
                              (.getColumn (name column))
                              (.getType))
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
