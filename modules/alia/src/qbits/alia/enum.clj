(ns qbits.alia.enum
  (:require
   [qbits.commons.enum :as enum])
  (:import
   [com.datastax.oss.driver.api.core DefaultConsistencyLevel]
   [com.datastax.oss.driver.api.core.loadbalancing NodeDistance]
   [com.datastax.oss.driver.api.core.servererrors DefaultWriteType]
   [com.datastax.oss.driver.api.core.cql DefaultBatchType]
   [com.datastax.oss.driver.api.core.config DefaultDriverOption]
   [java.util.concurrent TimeUnit]))

(def write-type (enum/enum->fn DefaultWriteType))
(def consistency-level (enum/enum->fn DefaultConsistencyLevel))
(def node-distance (enum/enum->fn NodeDistance))
;; TODO remove? compression options not on SessionBuilder
;; (def compression (enum/enum->fn ProtocolOptions$Compression))
(def batch-type (enum/enum->fn DefaultBatchType))
(def time-unit (enum/enum->fn TimeUnit))
(def driver-option (enum/enum->fn DefaultDriverOption))
