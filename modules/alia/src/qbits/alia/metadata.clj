(ns qbits.alia.metadata
  (:import
   [com.datastax.oss.driver.api.core CqlIdentifier]
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core.type
    DataType TupleType UserDefinedType]
   [com.datastax.oss.driver.api.core.data TupleValue UdtValue]
   [com.datastax.oss.driver.api.core.metadata Metadata]
   [com.datastax.oss.driver.api.core.metadata.schema
    KeyspaceMetadata
    TableMetadata
    ColumnMetadata]
   [java.util UUID List Map Set]
   [java.time Instant LocalDate LocalTime]
   [java.net InetAddress]
   [java.nio ByteBuffer]))

(defn get-keyspace-metadata
  "ks - keyspace name, if nil then default session keyspace will be used"
  [^Session session ks]
  (let [^Metadata metadata (.getMetadata session)

        ^KeyspaceMetadata ks-metadata
        (cond
          (some? ks)
          (some-> metadata
                  (.getKeyspace (name ks))
                  (.get))

          :else
          (some-> metadata
                  (.getKeyspace ^CqlIdentifier (.get (.getKeyspace session)))
                  (.get)))]

    ks-metadata))

(defn get-udt-metadata
  [^Session session ks type]
  (let [^KeyspaceMetadata ks-metadata
        (get-keyspace-metadata session ks)

        ^UserDefinedType udt
        (some-> ks-metadata
                (.getUserDefinedType (name type))
                (.get))]
    udt))

(defn get-table-metadata
  [^Session session ks table]
  (let [^KeyspaceMetadata ks-metadata
        (get-keyspace-metadata session ks)

        ^TableMetadata table-metadata
        (some-> ks-metadata
                (.getTable (name table))
                (.get))]

    table-metadata))

(defn get-column-metadata
  [^Session session ks table column]
  (let [^TableMetadata table-metadata
        (get-table-metadata session ks table)

        ^ColumnMetadata column-metadata
        (some-> table-metadata
                (.getColumn (name column))
                (.get))]

    column-metadata))

(defn get-column-type
  [^Session session ks table column]
  (let [^ColumnMetadata column-metadata
        (get-column-metadata session ks table column)

        ^DataType dt (some->
                      column-metadata
                      (.getType))]
    dt))
