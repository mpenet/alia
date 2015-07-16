(ns qbits.alia.enum
  (:require
   [qbits.commons.enum :as enum])
  (:import
   (com.datastax.driver.core
    BatchStatement$Type
    ConsistencyLevel
    HostDistance
    ProtocolOptions$Compression
    WriteType)))

(def write-type (enum/enum->map WriteType))
(def consistency-level (enum/enum->map ConsistencyLevel))
(def host-distance (enum/enum->map HostDistance))
(def compression (enum/enum->map ProtocolOptions$Compression))
(def batch-statement-type (enum/enum->map BatchStatement$Type))
