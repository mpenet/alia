(ns qbits.alia.codec.udt
  (:import
   (com.datastax.driver.core
    Session
    TupleValue
    UDTValue)
   (java.util
    UUID
    List
    Map
    Set
    Date)
   (java.net InetAddress)
   (java.nio ByteBuffer)))

(defprotocol PEncoder
  (-set-field! [x u k]))

(extend-protocol PEncoder

  BigInteger
  (-set-field! [b u k]
    (.setVarint ^UDTValue u ^String k b))

  Boolean
  (-set-field! [b u k]
    (.setBool ^UDTValue u ^String k  b))

  ByteBuffer
  (-set-field! [b u k]
    (.setBytes ^UDTValue u ^String k  b))

  Date
  (-set-field! [d u k]
    (.setDate ^UDTValue u ^String k  d))

  BigDecimal
  (-set-field! [d u k]
    (.setDecimal ^UDTValue u ^String k  d))

  Double
  (-set-field! [d u k]
    (.setDouble ^UDTValue u ^String k  d))

  Float
  (-set-field! [f u k]
    (.setFloat ^UDTValue u ^String k  f))

  InetAddress
  (-set-field! [i u k]
    (.setInet ^UDTValue u ^String k  i))

  Integer
  (-set-field! [i u k]
    (.setInt ^UDTValue u ^String k  i))

  List
  (-set-field! [l u k]
    (.setList ^UDTValue u ^String k  l))

  Long
  (-set-field! [l u k]
    (.setLong ^UDTValue u ^String k  l))

  Map
  (-set-field! [m u k]
    (.setMap ^UDTValue u ^String k  m))

  Set
  (-set-field! [s u k]
    (.setSet ^UDTValue u ^String k  s))

  String
  (-set-field! [x u k]
    (.setString ^UDTValue u ^String k  x))

  nil
  (-set-field! [n u k]
    (.setToNull ^UDTValue u ^String k))

  String
  (-set-field! [x u k]
    (.setString ^UDTValue u ^String k  x))

  TupleValue
  (-set-field! [t u k]
    (.setTupleValue ^UDTValue u ^String k  t))

  UDTValue
  (-set-field! [x u k]
    (.setUDTValue ^UDTValue u ^String k  x))

  UUID
  (-set-field! [uuid u k]
    (.setUUID ^UDTValue u ^String k  uuid)))

(defn set-field!
  "Where's flip when you need it"
  [u k x]
  (-set-field! x u k))

(defn encoder
  ([^Session session type]
   (encoder session
                (.getLoggedKeyspace session)
                type))
  ([^Session session ks type]
   (encoder session
                ks
                type
                nil))
  ([^Session session ks type opts]
   (let [t (-> session
                .getCluster
                .getMetadata
                (.getKeyspace (name ks))
                (.getUserType (name type)))]
     (fn [x]
       (let [utv (.newValue t)]
         (doseq [[k v] x]
           (set-field! utv (name k) v))
         utv)))))
