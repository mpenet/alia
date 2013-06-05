(ns qbits.alia.uuid
  (:import (com.datastax.driver.core.utils UUIDs)))

(defn time-based
  "Creates a new time-based (version 1) UUID."
  []
  (UUIDs/timeBased))

(defn start-of
  "Creates a 'fake' time-based UUID that sorts as the smallest
  possible version 1 UUID generated at the provided timestamp."
  [ts]
  (UUIDs/startOf (long ts)))

(defn end-of
  "Creates a 'fake' time-based UUID that sorts as the biggest possible
  version 1 UUID generated at the provided timestamp."
  [ts]
  (UUIDs/endOf (long ts)))

(defn unix-timestamp
  "Return the unix timestamp contained by the provided time-based UUID."
  [uuid]
  (UUIDs/unixTimestamp uuid))

(defn random
  "Creates a new random (version 4) UUID."
  []
  (UUIDs/random))
