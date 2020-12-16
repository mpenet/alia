(ns qbits.alia.result-set
  (:require
   [qbits.alia.gettable-by-index :as gettable-by-index])
  (:import
   [com.datastax.oss.driver.api.core
    CqlIdentifier]
   [com.datastax.oss.driver.api.core.cql
    ResultSet
    AsyncResultSet
    Row
    ColumnDefinitions
    ColumnDefinition]))

(defprotocol PResultSet
  (execution-info [this]))

(defprotocol PAsyncResultSetPage
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
           (keyword (.asInternal col-name))
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
                           (gettable-by-index/deserialize row idx decode))))))))

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

(defrecord AliaAsyncResultSetPage [current-page
                                   ^AsyncResultSet async-result-set
                                   next-page-handler]
  PResultSet
  (execution-info [this]
    (.getExecutionInfo async-result-set))

  PAsyncResultSetPage
  (current-page [this]
    (:current-page this))
  (fetch-next-page [this]
    (when next-page-handler
      (next-page-handler
       (.fetchNextPage async-result-set)))))

(defn async-result-set
  [^AsyncResultSet rs
   row-generator
   codec
   next-page-handler]
  (let [row-generator (or row-generator row-gen->map)
        decode (:decoder codec)
        current-page (.currentPage rs)
        page-rows (map
                   #(decode-row % row-generator decode)
                   current-page)
        has-more-pages? (.hasMorePages rs)]

    (map->AliaAsyncResultSetPage
     {:current-page page-rows
      :async-result-set rs
      :next-page-handler (when has-more-pages?
                           next-page-handler)})))
