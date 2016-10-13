(ns qbits.alia.codec
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

(declare decode-xform)

(defn deserialize [^GettableByIndexData x idx]
  (decode (.getObject x idx)))

(extend-protocol PCodec

  (Class/forName "[B")
  (encode [x] (ByteBuffer/wrap x))
  (decode [x] x)

  Map
  (encode [x] x)
  (decode [x]
    (->> x
         (reduce (fn [m [k v]]
                   (assoc! m k (decode v)))
                 (transient {}))
         persistent!))

  Set
  (encode [x] x)
  (decode [x]
    (into #{} decode-xform x))

  List
  (encode [x] x)
  (decode [x]
    (into [] decode-xform x))

  UDTValue
  (encode [x] x)
  (decode [udt-value]
    (let [udt-type (.getType udt-value)
          udt-type-iter (.iterator udt-type)
          len (.size udt-type)]
      (loop [udt (transient {})
             idx' 0]
        (if (= idx' len)
          (persistent! udt)
          (let [^UserType$Field type (.next udt-type-iter)]
            (recur (assoc! udt
                           (-> type .getName keyword)
                           (deserialize udt-value idx'))
                   (unchecked-inc-int idx')))))))

  TupleValue
  (encode [x] x)
  (decode [tuple-value]
    (let [len (.size (.getComponentTypes (.getType tuple-value)))]
      (loop [tuple (transient [])
             idx' 0]
        (if (= idx' len)
          (persistent! tuple)
          (recur (conj! tuple
                        (deserialize tuple-value idx'))
                 (unchecked-inc-int idx'))))))

  Object
  (encode [x] x)
  (decode [x] x)

  nil
  (decode [x] x)
  (encode [x] x))

(def ^:no-doc decode-xform (map decode))

(defprotocol PResultSet
  (execution-info [this]))

(defn lazy-result-set-
  [^ResultSet rs]
  (when-let [row (.one rs)]
    (lazy-seq (cons row (lazy-result-set- rs)))))

(defn lazy-result-set
  [^ResultSet rs]
  (lazy-seq (lazy-result-set- rs)))

;; Shamelessly inspired from https://github.com/ghadishayban/squee's
;; monoid'ish appoach
(defprotocol RowGenerator
  (init-row [this] "Constructs a row base")
  (conj-row [this r k v] "Adds a entry/col to a row")
  (finalize-row [this r] "\"completes\" the row"))

(def row-gen->map
  "Row Generator that map instances"
  (reify RowGenerator
    (init-row [_] (transient {}))
    (conj-row [_ row k v] (assoc! row (keyword k) v))
    (finalize-row [_ row] (persistent! row))))

(def row-gen->vector
  "Row Generator that builds vector instances"
  (reify RowGenerator
    (init-row [_] (transient []))
    (conj-row [_ row _ v] (conj! row v))
    (finalize-row [_ row] (persistent! row))))

(defn row-gen->record
  "Row Generator that builds record instances"
  [record-ctor]
  (reify RowGenerator
    (init-row [_] (transient {}))
    (conj-row [_ row k v] (assoc! row k v))
    (finalize-row [_ row] (-> row persistent! record-ctor))))

(defn decode-row
  [^Row row rg]
  (let [cdef (.getColumnDefinitions row)
        len (.size cdef)]
    (loop [idx (int 0)
           r (init-row rg)]
      (if (= idx len)
        (finalize-row rg r)
        (recur (unchecked-inc-int idx)
               (conj-row rg r
                         (.getName cdef idx)
                         (deserialize row idx)))))))

(defn ->result-set
  [^ResultSet rs row-generator]
  (let [row-generator (or row-generator row-gen->map)]
    (reify ResultSet
      PResultSet
      (execution-info [this]
        (.getAllExecutionInfo rs))
      clojure.lang.Seqable
      (seq [this]
        (map #(decode-row % row-generator)
             (lazy-result-set rs)))

      clojure.lang.IReduceInit
      (reduce [this f init]
        (loop [ret init]
          (if-let [row (.one rs)]
            (let [ret (f ret (decode-row row row-generator))]
              (if (reduced? ret)
                @ret
                (recur ret)))
            ret))))))

(defn result-set
  [^ResultSet rs result-set-fn row-generator]
  ((or result-set-fn seq) (->result-set rs row-generator)))

(defprotocol PNamedBinding
  "Bind the val onto Settable by name"
  (-set-named-parameter! [val settable name]))

(defn set-named-parameter!
  [^SettableByNameData settable name val]
  (-set-named-parameter! val settable name))

(extend-protocol PNamedBinding
  Boolean
  (-set-named-parameter! [val settable name]
    (.setBool ^SettableByNameData settable name val))

  Integer
  (-set-named-parameter! [val settable name]
    (.setInt ^SettableByNameData settable name val))

  Long
  (-set-named-parameter! [val settable name]
    (.setLong ^SettableByNameData settable name val))

  Date
  (-set-named-parameter! [val settable name]
    (.setTimestamp ^SettableByNameData settable name val))

  Float
  (-set-named-parameter! [val settable name]
    (.setFloat ^SettableByNameData settable name val))

  Double
  (-set-named-parameter! [val settable name]
    (.setDouble ^SettableByNameData settable name val))

  String
  (-set-named-parameter! [val settable name]
    (.setString ^SettableByNameData settable name val))

  ByteBuffer
  (-set-named-parameter! [val settable name]
    (.setBytes ^SettableByNameData settable name val))

  BigInteger
  (-set-named-parameter! [val settable name]
    (.setVarint ^SettableByNameData settable name val))

  BigDecimal
  (-set-named-parameter! [val settable name]
    (.setDecimal ^SettableByNameData settable name val))

  UUID
  (-set-named-parameter! [val settable name]
    (.setUUID ^SettableByNameData settable name val))

  InetAddress
  (-set-named-parameter! [val settable name]
    (.setInet ^SettableByNameData settable name val))

  List
  (-set-named-parameter! [val settable name]
    (.setList ^SettableByNameData settable name val))

  Map
  (-set-named-parameter! [val settable name]
    (.setMap ^SettableByNameData settable name val))

  Set
  (-set-named-parameter! [val settable name]
    (.setSet ^SettableByNameData settable name val))

  UDTValue
  (-set-named-parameter! [val settable name]
    (.setUDTValue ^SettableByNameData settable name val))

  TupleValue
  (-set-named-parameter! [val settable name]
    (.setTupleValue ^SettableByNameData settable name val))

  nil
  (-set-named-parameter! [_ settable name]
    (.setToNull ^SettableByNameData settable name)))
