(ns qbits.alia.enum
  (:require [clojure.string :as string])
  (:import
   (com.datastax.driver.core
    ConsistencyLevel
    HostDistance
    ProtocolOptions$Compression
    WriteType)))

(defn enum-values->map
  [enum-values]
  (reduce
   (fn [m hd]
     (assoc m (-> (.name ^Enum hd)
                  (.toLowerCase)
                  (string/replace "_" "-")
                  keyword)
            hd))
   {}
   enum-values))

(def write-type (enum-values->map (WriteType/values)))
(def consistency-level (enum-values->map (ConsistencyLevel/values)))
(def host-distance (enum-values->map (HostDistance/values)))
(def compression (enum-values->map (ProtocolOptions$Compression/values)))
