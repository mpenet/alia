(ns qbits.alia.manifold
  (:require
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [qbits.alia :as alia])
  (:import
   [com.datastax.oss.driver.api.core.session Session]
   [com.datastax.oss.driver.api.core CqlSession]
   [com.datastax.oss.driver.api.core.cql AsyncResultSet]))

(defn execute
  "Same as qbits.alia/execute, but returns just the first page of results"
  ([^CqlSession session query {:as opts}]
   (d/chain
    (alia/execute-async session query opts)
    :current-page))
  ([^Session session query]
     (execute session query {})))

(defn execute-buffered-pages
  ([^CqlSession session query {stream :stream
                               buffer-size :buffer-size
                               :as opts}]
   (let [stream (or stream
                    ;; fetch one page ahead by default
                    (s/stream (or buffer-size 1)))

         r-d (alia/execute-async session query opts)

         page-handler (fn page-stream-handler
                        [{current-page :current-page
                          ^AsyncResultSet async-result-set :async-result-set
                          next-page-handler :next-page-handler
                          :as args}]

                        (d/chain
                         (s/put! stream current-page)
                         (fn [put?]
                           (cond

                             ;; last page was put, there is another
                             (and put?
                                  next-page-handler)
                             (d/chain
                              (.fetchNextPage async-result-set)
                              next-page-handler
                              page-stream-handler)

                             ;; last page was the last
                             put?
                             (s/close! stream)

                             ;; bork!
                             :else
                             (throw
                              (ex-info
                               "qbits.alia.manifold/stream-put!-fail"
                               args))))))]

     (d/catch
         (d/chain r-d page-handler)
         (fn [err]
           (d/chain
            (s/put! stream err)
            (fn [put?]
              (when-not put?
                (binding [*out* *err*]
                  (prn "lost error" err)))))))

     stream))

  ([^CqlSession session query]
   (execute-buffered-pages session query {})))

(defn execute-buffered
  ([^CqlSession session query {:as opts}]
   (let [stream (execute-buffered-pages session query opts)]

     (s/transform
      (mapcat identity)
      stream)))

  ([^CqlSession session query]
   (execute-buffered-pages session query {})))
