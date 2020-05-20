(ns qbits.alia.codec
  (:import
   [java.nio ByteBuffer]
   [com.datastax.oss.driver.api.core.data
    GettableByIndex
    SettableByName
    UdtValue
    TupleValue]
   [com.datastax.oss.driver.api.core
    CqlIdentifier]
   [com.datastax.oss.driver.api.core.cql
    ResultSet
    AsyncResultSet
    Row
    ColumnDefinitions
    ColumnDefinition]
   [java.util UUID List Map Set]
   [java.time Instant LocalDate LocalTime]
   [java.net InetAddress]))

(defn deserialize [^GettableByIndex x idx decode]
  (prn x idx)
  (decode (.getObject x idx)))

(defprotocol PResultSet
  (execution-info [this]))

(defprotocol PAsyncResultSet
  (current-page [this])
  (fetch-next-page [this]))

;; Shamelessly inspired from https://github.com/ghadishayban/squee's
;; monoid'ish appoach
(defprotocol RowGenerator
  (init-row [this] "Constructs a row base")
  (conj-row [this r k v] "Adds a entry/col to a row")
  (finalize-row [this r] "\"completes\" the row"))

(defn create-row-gen->map-like
  "create a row-generator for map-like things"
  [{constructor :constructor
    table-ns? :table-ns?}]
  (reify RowGenerator
    (init-row [_] (transient {}))
    (conj-row [_ row col-def v]
      (if table-ns?
        (let [^CqlIdentifier col-name (.getName ^ColumnDefinition col-def)
              ^CqlIdentifier col-table (.getTable ^ColumnDefinition col-def)]
          (assoc!
           row
           (keyword (.asInternal col-table) (.asInternal col-name))
           v))
        (let [^CqlIdentifier col-name (.getName ^ColumnDefinition col-def)]
          (assoc!
           row
           (.asInternal col-name)
           v))))
    (finalize-row [_ row]
      (if constructor
        (constructor (persistent! row))
        (persistent! row)))))

(def row-gen->map
  "Row Generator that returns map instances"
  (create-row-gen->map-like {}))

(def row-gen->ns-map
  "row-generator creating maps with table-namespaced keys"
  (create-row-gen->map-like {:table-ns? true}))

(defn row-gen->record
  "Row Generator that builds record instances"
  [record-ctor]
  (create-row-gen->map-like {:constructor record-ctor}))

(defn row-gen->ns-record
  "Row Generator that builds record instances with table-namspaced keys"
  [record-ctor]
  (create-row-gen->map-like {:constructor record-ctor
                             :table-ns? true}))

(def row-gen->vector
  "Row Generator that builds vector instances"
  (reify RowGenerator
    (init-row [_] (transient []))
    (conj-row [_ row _ v] (conj! row v))
    (finalize-row [_ row] (persistent! row))))

(defn decode-row
  [^Row row rg decode]
  (let [^ColumnDefinitions cdefs (.getColumnDefinitions row)
        len (.size cdefs)]
    (loop [idx (int 0)
           r (init-row rg)]
      (if (= idx len)
        (finalize-row rg r)
        (recur (unchecked-inc-int idx)
               (let [^ColumnDefinition cdef (.get cdefs idx)]
                 (conj-row rg r
                           cdef
                           (deserialize row idx decode))))))))

(defn ->result-set
  [^ResultSet rs row-generator codec]
  (let [row-generator (or row-generator row-gen->map)
        decode (:decoder codec)]
    (reify ResultSet

      PResultSet
      (execution-info [this]
        (.getExecutionInfos rs))

      clojure.lang.Seqable
      (seq [this]
        (map #(decode-row % row-generator decode)
             rs))

      clojure.lang.IReduceInit
      (reduce [this f init]
        (loop [ret init]
          (if-let [row (.one rs)]
            (let [ret (f ret (decode-row row row-generator decode))]
              (if (reduced? ret)
                @ret
                (recur ret)))
            ret))))))

(defn result-set
  [^ResultSet rs result-set-fn row-generator codec]
  ((or result-set-fn seq) (->result-set rs row-generator codec)))

(defn async-result-set
  [^AsyncResultSet rs
   async-result-set-page-fn
   row-generator
   codec
   next-page-handler]
  (let [decode (:decoder codec)
        page-rows (map
                   #(decode-row % row-generator decode)
                   (.currentPage rs))
        page ((or async-result-set-page-fn seq) page-rows)]
    (reify
      PResultSet
      (execution-info [this]
        (.getExecutionInfo rs))

      PAsyncResultSet
      (current-page [this] page)
      (fetch-next-page [this]
        (next-page-handler
         (.fetchNextPage rs))))))

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
