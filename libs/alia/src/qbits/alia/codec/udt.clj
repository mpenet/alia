(ns qbits.alia.codec.udt
  (:require [qbits.alia.codec :as codec])
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
    (.setTimestamp ^UDTValue u ^String k  d))

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
  "Takes a Session, optionaly keyspace name, UDT name and returns a
  function that can be used to encode a map into a UDTValue suitable
  to be used in PreparedStatements"
  ([^Session session type]
   (encoder session (.getLoggedKeyspace session) type))
  ([^Session session ks type]
   (let [t (some-> session
                   .getCluster
                   .getMetadata
                   (.getKeyspace (name ks))
                   (.getUserType (name type)))]
     (when-not t
       (throw (ex-info (format "User Type '%s' not found on Keyspace '%s'"
                               (name type)
                               (name ks))
                       {:type ::type-not-found})))
     (fn [x]
       (let [utv (.newValue t)]
         (doseq [[k v] x]
           (set-field! utv (name k) (codec/encode v)))
         utv)))))
;; https://github.com/pyr/cyanite/issues/113
