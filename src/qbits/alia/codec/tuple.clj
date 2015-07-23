(ns qbits.alia.codec.tuple
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

(defprotocol PEncoder
  (-set-field! [x u i]))

(extend-protocol PEncoder

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
  ([^Session session table column]
   (encoder session
                (.getLoggedKeyspace session)
                table
                column))
  ([^Session session ks table column]
   (encoder session
                (.getLoggedKeyspace session)
                table
                column
                nil))
  ([^Session session ks table column opts]
   (let [^TupleType t (-> session
                          .getCluster
                          .getMetadata
                          (.getKeyspace (name ks))
                          (.getTable (name table))
                          (.getColumn (name column))
                          (.getType))]
     (fn [coll]
       (let [ttv (.newValue t)]
         (loop [i 0
                coll coll]
           (if-let [x (first coll)]
             (set-field! ttv i x)
             (recur (inc i) (rest coll))))
         ttv)))))
