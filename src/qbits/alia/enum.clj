(ns qbits.alia.enum
  (:require [clojure.string :as string])
  (:import
   (com.datastax.driver.core
    ConsistencyLevel
    HostDistance
    ProtocolOptions$Compression
    WriteType)))

(defn enum->map
  [enum]
  (reduce
   (fn [m hd]
     (assoc m (-> (.name ^Enum hd)
                  (.toLowerCase)
                  (string/replace "_" "-")
                  keyword)
            hd))
   {}
   (java.util.EnumSet/allOf enum)))

(def write-type (enum->map WriteType))
(def consistency-level (enum->map ConsistencyLevel))
(def host-distance (enum->map HostDistance))
(def compression (enum->map ProtocolOptions$Compression))
