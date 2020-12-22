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
   [com.datastax.oss.driver.api.core.cql AsyncResultSet]
   [java.util.concurrent CompletionStage]))

(defn execute-deferred
  "Same as qbits.alia/execute, but returns just the first page of results"
  ([^CqlSession session query {:as opts}]
   (d/chain
    (alia/execute-async session query opts)
    :current-page))
  ([^Session session query]
     (execute-deferred session query {})))

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

(defn execute-stream-records
  ([^CqlSession session query {:as opts}]
   (let [stream (execute-stream-pages session query opts)]

     (s/transform
      (mapcat safe-identity)
      stream)))

  ([^CqlSession session query]
   (execute-stream-records session query {})))

;; backwards compatible fn name
(def execute-buffered execute-stream-records)
