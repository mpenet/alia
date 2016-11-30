(ns qbits.alia.codec.udt-aware
  (:require
   [clojure.reflect :as reflect]
   [qbits.alia.codec :as codec]
   [qbits.alia.codec.default :as default-codec]
   [qbits.alia.udt :as udt])
  (:import
   (java.nio ByteBuffer)
   (com.datastax.driver.core
    DataType
    DataType$Name
    GettableByIndexData
    ResultSet
    Row
    UserType$Field
    Session
    SettableByNameData
    UDTValue
    TupleType
    TupleValue)
   (java.util UUID List Map Set Date)
   (java.net InetAddress)))

(declare codec)

(defprotocol Encoder
  (encode [x]
    "Defines how to do the encoding of value via a codec instance
    function"))

(defprotocol Decoder
  (decode [x]
    "Defines how to do the decoding of value via a codec instance
    function"))

(defprotocol UDTRegistry
  (register-udt! [this session udt-name record-map-constructor]
    "Allows to register an udtencoder at codec level, when the
    specified record typed is passed it will automatically be encoded
    in the appropriate UDTValue")
  (deregister-udt! [this session udt-name record-map-constructor]
    "Allows to register an udtencoder at codec level, when the
    specified record typed is passed it will automatically be encoded
    in the appropriate UDTValue")
  (get-udt-codec [this udt-name-or-record-type]))

;; we use extend to allow end users to compose from these if they
;; need/want to. extend-protocol expands to that anyway
(defn decoders [decode]
  (merge (default-codec/decoders decode)
         {:UDTValue
          #(let [^UDTValue udt-value %
                udt-type (.getType udt-value)
                udt-type-iter (.iterator udt-type)
                len (.size udt-type)]
            (loop [udt (transient {})
                   idx' 0]
              (if (= idx' len)
                (let [m (persistent! udt)]
                  (if-let [record-ctor (get-udt-codec codec (.getTypeName udt-type))]
                    (record-ctor m)
                    m))
                (let [^UserType$Field type (.next udt-type-iter)]
                  (recur (assoc! udt
                                 (-> type .getName keyword)
                                 (codec/deserialize udt-value idx' decode))
                         (unchecked-inc-int idx'))))))}))

(defn encoders [encode]
  (merge (default-codec/encoders encode)
         {:IRecord #(if-let [codec-fn (get-udt-codec codec (type %))]
                      (codec-fn %)
                      %)}))

(def default-decoders (decoders #'decode))
(def default-encoders (encoders #'encode))

(defn record-map-ctor [rec-sym]
  (let [[_ ns' kls] (re-find #"(.*)\.([^.]*)$" (str (reflect/typename rec-sym)))]
    (resolve (symbol (str ns' "/map->" kls)))))

(defrecord DefaultCodec [encoder decoder udt-registry]
  UDTRegistry
  (deregister-udt! [this session udt-name ctor]
    (vswap! udt-registry  dissoc
            ;; name -> record-ctor
            (name udt-name)
            ;; record-type -> udtencoder
            ctor))
  (register-udt! [this session udt-name record-ctor]
    ;; we register both ways as we need inverted index depending on if
    ;; it's for decoding/encoding
    (vswap! udt-registry assoc
            ;; name -> record-ctor
            (name udt-name) (record-map-ctor record-ctor)
            ;; record-type -> udtencoder
            record-ctor
            (udt/encoder session
                         udt-name
                         codec)))
  (get-udt-codec [this udt-name-or-rec-type]
    (get @udt-registry udt-name-or-rec-type)))

(defonce codec
  (map->DefaultCodec
   {:encoder #'encode
    :decoder #'decode
    :udt-registry (volatile! {})}))

(extend Map Decoder {:decode (:Map default-decoders)})
(extend List Decoder {:decode (:List default-decoders)})
(extend Set Decoder {:decode (:Set default-decoders)})
(extend UDTValue Decoder {:decode (:UDTValue default-decoders)})
(extend TupleValue Decoder {:decode (:TupleValue default-decoders)})
(extend Object Decoder {:decode (:Object default-decoders)})
(extend nil Decoder {:decode (:nil default-decoders)})

(extend (Class/forName "[B") Encoder {:encode (:bytes default-encoders)})
(extend clojure.lang.IRecord Encoder {:encode (:IRecord default-encoders)})
(extend Object Encoder {:encode (:Object default-encoders)})
(extend nil Encoder {:encode (:nil default-encoders)})
