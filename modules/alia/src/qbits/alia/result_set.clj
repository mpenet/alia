(ns qbits.alia.result-set
  (:require
   [qbits.alia.gettable-by-index :as gettable-by-index]
   [qbits.alia.completable-future :as cf]
   [qbits.alia.error :as err])
  (:import
   [com.datastax.oss.driver.api.core
    CqlIdentifier]
   [com.datastax.oss.driver.api.core.cql
    ResultSet
    AsyncResultSet
    Row
    ColumnDefinitions
    ColumnDefinition]
   [java.util.concurrent Executor CompletionStage ExecutionException]))

;; defintes the interface of the object given to the :result-set-fn
;; for `qbits.alia/execute` queries, along with ISeqable and IReduceInit
(defprotocol PResultSet
  (execution-infos [this]))

;; defines the interface of the object given to the :result-set-fn
;; for each page of `qbits.alia/execute-async` queries, along with
;; ISeqable and IReduceInit
(defprotocol PAsyncResultSet
  (execution-info [this]))

;; defines the type returned by execute-async
(defprotocol PAsyncResultSetPage
  (has-more-pages? [this]
    "returns true when there are more pages to fetch")
  (fetch-next-page [this]
    "returns a `CompletedFuture<PAsyncResultSetPage>` of the next
     page, or, if `this` was the final page, or the next page
     is empty, returns `CompletedFuture<nil>`"))

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

(defn row-decoder-xform
  [row-generator
   {decoder :decoder :as codec}]
  (map
   #(decode-row % (or row-generator row-gen->map) decoder)))

(defn ->result-set
  "ISeqable and IReduceInit support for a ResultSet, to
   be given to the :result-set-fn for sync query results

   cf: clojure.lang.Eduction - the returned object is similar, but
   also permits the retrieval of execution-infos"
  [^ResultSet rs decoder-xform]
  (reify ResultSet

    PResultSet
    (execution-infos [this]
      (.getExecutionInfos rs))

    java.lang.Iterable
    (iterator [this]
      (clojure.lang.TransformerIterator/create
       decoder-xform
       (clojure.lang.RT/iter rs)))

    clojure.lang.IReduceInit
    (reduce [this f init]
      (transduce decoder-xform (completing f) init rs))

    clojure.lang.Sequential))

(defn result-set
  [^ResultSet rs
   result-set-fn
   row-generator
   codec]
  ((or result-set-fn seq) (->result-set
                           rs
                           (row-decoder-xform row-generator codec))))

(defn ->iterable-async-result-set
  "ISeqable and IReduceInit support for an AsyncResultSet, to
   be given to the :result-set-fn to create the :current-page object

   cf: clojure.lang.Eduction - the returned object is similar, but
   also permits the retrieval of execution-info"
  [^AsyncResultSet async-result-set decoder-xform]
  (reify

    PAsyncResultSet
    (execution-info [this]
      (.getExecutionInfo async-result-set))

    java.lang.Iterable
    (iterator [this]
      (clojure.lang.TransformerIterator/create
       decoder-xform
       (clojure.lang.RT/iter
        (.currentPage async-result-set))))

    clojure.lang.IReduceInit
    (reduce [this f init]
      (transduce
       decoder-xform
       (completing f)
       init
       (.currentPage async-result-set)))

    clojure.lang.Sequential))

(declare handle-async-result-set-completion-stage)

;; the primary type returned by execute-async
(defrecord AliaAsyncResultSetPage [^AsyncResultSet async-result-set
                                   current-page
                                   opts]

  PAsyncResultSet
  (execution-info [this]
    (.getExecutionInfo async-result-set))

  PAsyncResultSetPage

  (has-more-pages? [this]
    (.hasMorePages async-result-set))

  (fetch-next-page [this]
    (if (true? (.hasMorePages async-result-set))
      (handle-async-result-set-completion-stage
       (.fetchNextPage async-result-set)
       opts)

      ;; return a CompletedFuture<nil> rather than plain nil
      ;; so the consumer get to expect a uniform type
      (cf/completed-future nil))))

(defn async-result-set-page
  "make a single `AliaAsyncResultSetPage` of an `execute-async` result

   if the current-page is the last page and is empty then `nil` is returned

   the records from the current page are in the `:current-page` field
   which is constructed from an `Iterable`/`IReduceInit` `AsyncResultSet`
   wrapper by the `:result-set-fn` (which defaults to `clojure.core/seq`)

   subsequent pages can be fetched with `PAsyncResultSetPage/fetch-next-page`,
   which is defined as a record rather than an opaque type to aid with debugging"
  [^AsyncResultSet ars
   {result-set-fn :result-set-fn
    row-generator :row-generator
    codec :codec
    :as opts}]
  (let [empty-page? (= 0 (.remaining ars))
        hmp? (.hasMorePages ars)]

    (cond
      ;; if the :page-size lines up with the result-set size, then
      ;; an empty page happens
      (and (true? empty-page?)
           (false? hmp?))
      nil

      (false? empty-page?)
      (let [iterable-ars (->iterable-async-result-set
                          ars
                          (row-decoder-xform row-generator codec))

            current-page ((or result-set-fn seq) iterable-ars)]

        (map->AliaAsyncResultSetPage
         {:async-result-set ars
          :current-page current-page
          :opts opts}))

      :else
      (throw
       (ex-info
        "empty page before end of results?"
        {:async-result-set ars})))))

(defn handle-async-result-set-completion-stage
  "handle a `CompletionStage` resulting from an `.executeAsync` of a query

   when successful, applies the `:row-generator` and `:result-set-fn`
   to the current page
   when failed, decorates the exception with query and value details"
  [^CompletionStage completion-stage
   {:keys [codec
           result-set-fn
           row-generator
           executor
           statement
           values]
    :as opts}]

  (cf/handle-completion-stage
   completion-stage

   (fn [async-result-set]
     (async-result-set-page async-result-set opts))

   (fn [err]
     (throw
      (err/ex->ex-info err {:statement statement :values values})))

   opts))
