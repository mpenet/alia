(ns qbits.alia.codec
  (:import
   [java.nio ByteBuffer]
   [com.datastax.oss.driver.api.core.data
    GettableByIndex
    SettableByName
    UdtValue
    TupleValue]
   [java.util UUID List Map Set]
   [java.time Instant LocalDate LocalTime]
   [java.net InetAddress]))

(defn deserialize [^GettableByIndex x idx decode]
  (decode (.getObject x idx)))

(defprotocol PNamedBinding
  "Bind the val onto Settable by name"
  (-set-named-parameter!
    [val settable name]
    [val settable name el-class]
    [val settable name k-class v-class]))

(defn set-named-parameter!
  ([^SettableByName settable name val]
   (-set-named-parameter! val settable name))
  ([^SettableByName settable name val el-class]
   (-set-named-parameter! val settable name el-class))
  ([^SettableByName settable name val k-class v-class]
   (-set-named-parameter! val settable name k-class v-class)))

(extend-protocol PNamedBinding
  Boolean
  (-set-named-parameter! [val settable name]
    (.setBoolean ^SettableByName settable ^String name val))

  Integer
  (-set-named-parameter! [val settable name]
    (.setInt ^SettableByName settable ^String name val))

  Long
  (-set-named-parameter! [val settable name]
    (.setLong ^SettableByName settable ^String name val))

  Instant
  (-set-named-parameter! [val settable name]
    (.setInstant ^SettableByName settable ^String name val))

  LocalDate
  (-set-named-parameter! [val settable name]
    (.setLocalDate ^SettableByName settable ^String name val))

  LocalTime
  (-set-named-parameter! [val settable name]
    (.setLocalTime ^SettableByName settable ^String name val))

  Float
  (-set-named-parameter! [val settable name]
    (.setFloat ^SettableByName settable ^String name val))

  Double
  (-set-named-parameter! [val settable name]
    (.setDouble ^SettableByName settable ^String name val))

  String
  (-set-named-parameter! [val settable name]
    (.setString ^SettableByName settable ^String name val))

  ByteBuffer
  (-set-named-parameter! [val settable name]
    (.setByteBuffer ^SettableByName settable ^String name val))

  BigInteger
  (-set-named-parameter! [val settable name]
    (.setBigInteger ^SettableByName settable ^String name val))

  BigDecimal
  (-set-named-parameter! [val settable name]
    (.setBigDecimal ^SettableByName settable ^String name val))

  UUID
  (-set-named-parameter! [val settable name]
    (.setUuid ^SettableByName settable ^String name val))

  InetAddress
  (-set-named-parameter! [val settable name]
    (.setInetAddress ^SettableByName settable ^String name val))

  List
  (-set-named-parameter! [val settable name el-class]
    (.setList ^SettableByName settable ^String name val ^Class el-class))

  Map
  (-set-named-parameter! [val settable name k-class v-class]
    (.setMap ^SettableByName settable ^String name val ^Class k-class ^Class v-class))

  Set
  (-set-named-parameter! [val settable name el-class]
    (.setSet ^SettableByName settable ^String name val ^Class el-class))

  UdtValue
  (-set-named-parameter! [val settable name]
    (.setUdtValue ^SettableByName settable ^String name val))

  TupleValue
  (-set-named-parameter! [val settable name]
    (.setTupleValue ^SettableByName settable ^String name val))

  nil
  (-set-named-parameter! [_ settable name]
    (.setToNull ^SettableByName settable ^String name)))
