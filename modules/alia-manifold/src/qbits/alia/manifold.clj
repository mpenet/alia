(ns qbits.alia.manifold
  (:require
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [qbits.alia :as alia]
   [qbits.alia.completable-future :as cf]
   [qbits.alia.result-set :as result-set])
  (:import
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core CqlSession]
   [java.util.concurrent CompletionStage]))



(defn handle-page-completion-stage
  [^CompletionStage completion-stage
   {stream :stream
    :as opts}]
  (cf/handle-completion-stage
   completion-stage

   (fn [{current-page :current-page
        :as async-result-set-page}]

     (if (some? async-result-set-page)

       (d/chain
        (s/put! stream current-page)
        (fn [put?]
          (cond

            ;; last page put ok and there is another
            (and put?
                 (true? (result-set/has-more-pages? async-result-set-page)))
            (handle-page-completion-stage
             (result-set/fetch-next-page async-result-set-page)
             opts)

            ;; last page put ok and was the last
            put?
            (s/close! stream)

            ;; bork! last page did not put.
            ;; maybe the stream was closed?
            :else
            (throw
             (ex-info
              "qbits.alia.manifold/stream-put!-fail"
              (merge val (select-keys opts [:statement :values])))))))

       ;; edge-case - when :page-size lines up with result size,
       ;; the final page is empty, resulting in a nil async-result-set
       (s/close! stream)))

   (fn [err]
     (d/finally
       (s/put! stream err)
       (fn [] (s/close! stream))))

   opts))

(defn execute-stream-pages
  "similar to `qbits.alia/execute`, but executes async and returns a
   `Stream<AliaAsyncResultSetPage>`

   the `:current-page` of each `AliaAsyncResultSetPage` is built by
   applying `:result-set-fn` (default `clojure.core/seq`) to an
   `Iterable` + `IReduceInit` supporting version of the `AsyncResultSet`

   supports all the args of `qbits.alia/execute` and:

   - `:page-buffer-size` determines the number of pages to buffer ahead,
      defaults to 1
   - `:stream` - optional - the stream to copy records to, defaults to a new
      stream with buffer size `:page-buffer-size`"
  ([^CqlSession session query {stream :stream
                               page-buffer-size :page-buffer-size
                               :as opts}]
   (let [stream (or stream
                    ;; fetch one page ahead by default
                    (s/stream (or page-buffer-size 1)))

         page-cs (alia/execute-async session query opts)]

     (handle-page-completion-stage
      page-cs
      (merge opts
             {:stream stream
              :statement query}))

     stream))

  ([^CqlSession session query]
   (execute-stream-pages session query {})))


(defn ^:private safe-identity
  "if the page should happen to be an Exception, wrap
   it in a vector so that it can be concatenated to the
   value stream"
  [v]
  (if (sequential? v) v [v]))

(defn execute-stream
  "like `execute-stream-pages`, but returns a `Stream<row>`

   supports all the args of `execute-stream-pages`"
  ([^CqlSession session query {:as opts}]
   (let [stream (execute-stream-pages session query opts)]

     (s/transform
      (mapcat safe-identity)
      stream)))

  ([^CqlSession session query]
   (execute-stream session query {})))

;; backwards compatible fn name
(def execute-buffered execute-stream)

(defn execute
  "similar to `qbits.alia/execute`, but executes async
   - returns a `Deferred<records>` with all the records
     from all pages of results realised in memory

   any errors will error the result Deferred"
  ([^CqlSession session query {:as opts}]
   (let [pages-s (execute-stream-pages session query opts)
         pages-d (s/reduce
                  (fn [pages page-or-error]
                    ;; make sure errors get propagated
                    (if (instance? Throwable page-or-error)
                      (throw page-or-error)
                      (conj pages page-or-error)))
                  []
                  pages-s)]
     (d/chain
      pages-d
      (fn [pages]
        (apply concat pages)))))
  ([^Session session query]
   (execute session query {})))
