(ns qbits.alia.async
  (:require
   [clojure.core.async :as async]
   [qbits.alia :as alia]
   [qbits.alia.completable-future :as cf])
  (:import
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core CqlSession]
   [com.datastax.oss.driver.api.core.cql AsyncResultSet]
   [java.util.concurrent CompletionStage]
   [java.util.function BiFunction]))

(defn handle-page-completion-stage
  [^CompletionStage completion-stage
   {statement :statement
    values :values
    chan :chan
    executor :executor
    stop? ::stop?
    :as opts}]
  (cf/handle-completion-stage
   completion-stage
   (fn [{current-page :current-page
        ^AsyncResultSet async-result-set :async-result-set
        next-page-handler :next-page-handler
        :as val}]
     (async/put!
      chan
      current-page
      (fn [put?]
        (cond

          ;; stop requested at this page (probably for promise-chan)
          stop?
          (async/close! chan)

          ;; last page put ok, and there is another
          (and put? next-page-handler)
          (-> (.fetchNextPage async-result-set)
              next-page-handler
              (handle-page-completion-stage opts))

          ;; last page put ok, and was the last
          put?
          (async/close! chan)

          :else
          (throw
           (ex-info
            "qbits.alia.async/handle-page-completion-stage"
            (merge val (select-keys opts [:statement :values]))))))))

   (fn [err]
     (async/put!
      chan
      (alia/ex->ex-info
       err
       (select-keys opts [:statement :values]))
      (fn [_]
        (async/close! chan))))

   opts))

(defn execute-chan-pages
  ([^CqlSession session query {chan :chan
                               page-buffer-size :page-buffer-size
                               :as opts}]
   (let [chan (or chan
                  ;; fetch one page ahead by default
                  (async/chan (or page-buffer-size 1)))

         page-cs (alia/execute-async session query opts)]

     (handle-page-completion-stage
      page-cs
      (merge opts
             {:chan chan
              :statement query}))

     chan))

  ([^CqlSession session query]
   (execute-chan-pages session query {})))

(defn execute-chan
  "Same as execute, but returns a clojure.core.async/promise-chan that is
  wired to the underlying ResultSetFuture. This means this is usable
  with `go` blocks or `take!`. Exceptions are sent to the channel as a
  value, it's your responsability to handle these how you deem
  appropriate.

  For options refer to `qbits.alia/execute` doc"
  ([^CqlSession session query {chan :chan
                               :as opts}]
   (let [chan (or chan (async/promise-chan))

         page-cs (alia/execute-async session query opts)]

     (handle-page-completion-stage
      page-cs
      (merge opts
             {:chan chan
              :statement query
              ::stop? true}))

     chan))

  ([^Session session query]
   (execute-chan session query {})))

(defn ^:private safe-identity
  "if the page should happen to be an Exception, wrap
   it in a vector so that it can be concatenated to the
   value channel"
  [v]
  (if (sequential? v) v [v]))

(defn execute-chan-records
  "Allows to execute a query and have rows returned in a
  `clojure.core.async/chan`. Every value in the chan is a single
  row. By default the query `:page-size` inherits from the cluster
  setting, unless you specify a different `:page-size` at query level
  and the channel is a regular `clojure.core.async/chan`, unless you
  pass your own `:channel` with its own sizing
  caracteristics. `:page-size` dicts the chunking of the rows
  returned, allowing to stream rows into the channel in a controlled
  manner.
  If you close the channel the streaming process ends.
  Exceptions are sent to the channel as a value, it's your
  responsability to handle these how you deem appropriate. For options
  refer to `qbits.alia/execute` doc"
  ([^CqlSession session query {out-chan :chan
                               :as opts}]

   (let [page-chan (execute-chan-pages session query (dissoc opts :chan))
         record-chan (async/chan 1 (mapcat safe-identity))]

     (async/pipe page-chan record-chan)

     (if (some? out-chan)
       (do
         (async/pipe record-chan out-chan)
         out-chan)
       record-chan)))

  ([^Session session query]
   (execute-chan-records session query {})))
