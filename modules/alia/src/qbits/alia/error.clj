(ns qbits.alia.error
  (:import
   [java.util.concurrent ExecutionException]))

(defn ^:no-doc ex->ex-info
  "wrap an exception with some context information"
  ([^Exception ex data msg]
   (ex-info msg
            (merge {:type ::execute
                    :exception ex}
                   data)
            (if (instance? ExecutionException ex)
              (.getCause ex)
              ex)))
  ([ex data]
   (ex->ex-info ex data "Query execution failed")))
