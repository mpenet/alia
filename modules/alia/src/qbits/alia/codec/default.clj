(ns qbits.alia.codec.default
  (:require
   [qbits.alia.gettable-by-index :as gettable-by-index])
  (:import
   [java.nio ByteBuffer]
   [com.datastax.oss.driver.api.core
    CqlIdentifier]
   [com.datastax.oss.driver.api.core.type
    UserDefinedType]
   [com.datastax.oss.driver.api.core.data
    UdtValue
    TupleValue]
   [java.util List Map Set]))

(defprotocol Encoder
  (encode [x]
    "Defines how to do the encoding of value via a codec instance
    function"))

(defprotocol Decoder
  (decode [x]
    "Defines how to do the decoding of value via a codec instance
    function"))

;; We use extend to allow end users to compose from these if they
;; need/want to. extend-protocol expands to that anyway the decode
;; function param is here so that potential reuse works with recursive
;; decoding in non-scalar values.
(defn decoders [decode]
  (let [decode-xform (map decode)]
    {:Map #(->> %
                (reduce (fn [m [k v]]
                          (assoc! m k (decode v)))
                        (transient {}))
                persistent!)
     :List #(into [] decode-xform %)
     :Set #(into #{} decode-xform %)
     :UdtValue
     #(let [^UdtValue udt-value %
            ^UserDefinedType udt-type (.getType udt-value)
            len (.size udt-value)
            field-ids (.getFieldNames udt-type)]
        (into
         {}
         (map (fn [i]
                (let [^CqlIdentifier field-id (.get field-ids i)
                      field-name (-> field-id .asInternal keyword)
                      val (gettable-by-index/deserialize udt-value i decode)]
                  [field-name val])))
         (range len)))
     :TupleValue
     #(let [^TupleValue tuple-value %
            len (.size (.getComponentTypes (.getType tuple-value)))]
        (loop [tuple (transient [])
               idx' 0]
          (if (= idx' len)
            (persistent! tuple)
            (recur (conj! tuple
                          (gettable-by-index/deserialize tuple-value idx' decode))
                   (unchecked-inc-int idx')))))
     :Object identity
     :nil (fn [_] nil)}))

(defn encoders [encode]
  {:bytes #(ByteBuffer/wrap %)
   :Object identity
   :nil (fn [_] nil)})

(def default-decoders (decoders #'decode))
(def default-encoders (encoders #'encode))

(defrecord DefaultCodec [encoder decoder])

(defonce codec
  (map->DefaultCodec
   {:encoder #'encode
    :decoder #'decode}))

(extend Map Decoder {:decode (:Map default-decoders)})
(extend List Decoder {:decode (:List default-decoders)})
(extend Set Decoder {:decode (:Set default-decoders)})
(extend UdtValue Decoder {:decode (:UdtValue default-decoders)})
(extend TupleValue Decoder {:decode (:TupleValue default-decoders)})
(extend Object Decoder {:decode (:Object default-decoders)})
(extend nil Decoder {:decode (:nil default-decoders)})

(extend (Class/forName "[B") Encoder {:encode (:bytes default-encoders)})
(extend Object Encoder {:encode (:Object default-encoders)})
(extend nil Encoder {:encode (:nil default-encoders)})
