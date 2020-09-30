(ns qbits.alia.metadata
  (:import
   [com.datastax.oss.driver.api.core CqlIdentifier]
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core.type
    DataType DataTypes TupleType UserDefinedType]
   [com.datastax.oss.driver.api.core.data TupleValue UdtValue]
   [com.datastax.oss.driver.api.core.metadata Metadata]
   [com.datastax.oss.driver.api.core.metadata.schema
    KeyspaceMetadata
    TableMetadata
    ColumnMetadata]
   [java.util UUID List Map Set Optional]
   [java.time Instant LocalDate LocalTime Duration]
   [java.net InetAddress]
   [java.nio ByteBuffer]))

(defn safe-get
  [^Optional opt]
  (if (.isPresent opt)
    (.get opt)
    nil))

(defn get-keyspace-metadata
  "ks - keyspace name, if nil then default session keyspace will be used"
  [^Session session ks]
  (let [^Metadata metadata (.getMetadata session)

        ^KeyspaceMetadata ks-metadata
        (cond
          (some? ks)
          (some-> metadata
                  (.getKeyspace (name ks))
                  (safe-get))

          :else
          (some-> metadata
                  (.getKeyspace ^CqlIdentifier (.get (.getKeyspace session)))
                  (safe-get)))]

    ks-metadata))

(defn get-udt-metadata
  [^Session session ks type]
  (let [^KeyspaceMetadata ks-metadata
        (get-keyspace-metadata session ks)

        ^UserDefinedType udt
        (some-> ks-metadata
                (.getUserDefinedType (name type))
                (safe-get))]
    udt))

(defn get-table-metadata
  [^Session session ks table]
  (let [^KeyspaceMetadata ks-metadata
        (get-keyspace-metadata session ks)

        ^TableMetadata table-metadata
        (some-> ks-metadata
                (.getTable (name table))
                (safe-get))]

    table-metadata))

(defn get-column-metadata
  [^Session session ks table column]
  (let [^TableMetadata table-metadata
        (get-table-metadata session ks table)

        ^ColumnMetadata column-metadata
        (some-> table-metadata
                (.getColumn (name column))
                (safe-get))]

    column-metadata))

(defn get-column-type
  [^Session session ks table column]
  (let [^ColumnMetadata column-metadata
        (get-column-metadata session ks table column)

        ^DataType dt (some->
                      column-metadata
                      (.getType))]
    dt))

(def default-classes
  {DataTypes/ASCII String
   DataTypes/BIGINT BigInteger
   DataTypes/BLOB (.getClass (byte-array []))
   DataTypes/BOOLEAN Boolean
   DataTypes/COUNTER Integer
   DataTypes/DATE Instant
   DataTypes/DECIMAL Float
   DataTypes/DOUBLE Double
   DataTypes/DURATION Duration
   DataTypes/FLOAT Float
   DataTypes/INET String
   DataTypes/INT Integer
   DataTypes/SMALLINT Short
   DataTypes/TEXT String
   DataTypes/TIME Instant
   DataTypes/TIMESTAMP Instant
   DataTypes/TIMEUUID UUID
   DataTypes/TINYINT Byte
   DataTypes/UUID UUID
   DataTypes/VARINT BigDecimal})

(defn default-class
  [dt ec]
  (or ec (get default-classes dt Object)))

(defn cql-id->kw
  [^CqlIdentifier cql-id]
  (keyword (.asInternal cql-id)))
