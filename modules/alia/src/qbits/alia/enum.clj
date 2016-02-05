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

(def write-type (enum/enum->fn WriteType))
(def consistency-level (enum/enum->fn ConsistencyLevel))
(def host-distance (enum/enum->fn HostDistance))
(def compression (enum/enum->fn ProtocolOptions$Compression))
(def batch-statement-type (enum/enum->fn BatchStatement$Type))

