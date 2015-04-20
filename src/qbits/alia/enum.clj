(ns qbits.alia.enum
  (:require
   [qbits.commons.enum :as enum])
  (:import
   (com.datastax.driver.core
    ConsistencyLevel
    HostDistance
    ProtocolOptions$Compression
    WriteType)))

(def write-type (enum/enum->map WriteType))
(def consistency-level (enum/enum->map ConsistencyLevel))
(def host-distance (enum/enum->map HostDistance))
(def compression (enum/enum->map ProtocolOptions$Compression))
