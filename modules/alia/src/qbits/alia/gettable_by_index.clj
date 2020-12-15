(ns qbits.alia.gettable-by-index
  (:import
   [com.datastax.oss.driver.api.core.data GettableByIndex]))

(defn deserialize [^GettableByIndex x idx decode]
  (decode (.getObject x idx)))
