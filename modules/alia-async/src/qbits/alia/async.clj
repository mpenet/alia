(ns qbits.alia.async
  (:require
   [clojure.core.async :as async]
   [qbits.alia :as alia]
   [qbits.alia.completable-future :as cf]
   [qbits.alia.error :as err]
   [qbits.alia.result-set :as result-set])
  (:import
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core CqlSession]
   [java.util.concurrent CompletionStage]))

(defn handle-page-completion-stage
  [^CompletionStage completion-stage
   {chan :chan
    stop? ::stop?
    :as opts}]
  (cf/handle-completion-stage
   completion-stage
   (fn [{current-page :current-page
        :as async-result-set-page}]

     (if (some? async-result-set-page)
       (async/put!
        chan
        current-page
        (fn [put?]
          (cond

            ;; stop requested at this page (probably for promise-chan)
            stop?
            (async/close! chan)

            ;; last page put ok, and there is another
            (and put?
                 (true? (result-set/has-more-pages? async-result-set-page)))
            (handle-page-completion-stage
             (result-set/fetch-next-page async-result-set-page)
             opts)

            ;; last page put ok, and was the last
            put?
            (async/close! chan)

            :else
            (throw
             (ex-info
              "qbits.alia.async/handle-page-completion-stage"
              (merge val (select-keys opts [:statement :values])))))))

       ;; edge-case - when :page-size lines up with result size,
       ;; the final page is empty, resulting in a nil async-result-set
       (async/close! chan)))

   (fn [err]
     (async/put!
      chan
      (err/ex->ex-info
       err
       (select-keys opts [:statement :values]))
      (fn [_]
        (async/close! chan))))

   opts))

(defn execute
  "similar to `qbits.alia/execute`, but returns a
   `clojure.core.async/promise-chan` with just the first page of
   results. Exceptions are sent to the channel as a
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
   (execute session query {})))

(defn execute-chan-pages
  "similar to `qbits.alia/execute`, but executes async and returns a
   `clojure.core.async/chan<AliaAsyncResultSetPage>`

   the `:current-page` of each `AliaAsyncResultSetPage` is built by
   applying `:result-set-fn` (default `clojure.core/seq`) to an
   `Iterable` + `IReduceInit` supporting version of the `AsyncResultSet`

   supports all the args of `qbits.alia/execute` and:

   - `:page-buffer-size` determines the number of pages to buffer ahead,
      defaults to 1
   - `:chan` - optional - the channel to copy records to, defaults to a new
      `clojure.core.async/chan` with buffer size `:page-buffer-size`"
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

(defn ^:private safe-identity
  "if the page should happen to be an Exception, wrap
   it in a vector so that it can be concatenated to the
   value channel"
  [v]
  (if (sequential? v) v [v]))

(defn execute-chan
  "like `execute-chan-pages`, but returns a `clojure.core.async/chan<row>`

   supports all the args of `execute-chan-pages`"
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
   (execute-chan session query {})))

;; backwards compatible name
(def execute-buffered execute-chan)
