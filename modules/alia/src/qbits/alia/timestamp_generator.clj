(ns qbits.alia.timestamp-generator
  (:import
   [com.datastax.oss.driver.api.core.context DriverContext]
   [com.datastax.oss.driver.internal.core.time
    AtomicTimestampGenerator
    ServerSideTimestampGenerator
    ThreadLocalTimestampGenerator]))

(defn atomic-monotonic
  "A timestamp generator based on System.currentTimeMillis(), with an
  incrementing atomic counter to generate the sub-millisecond part.

  This implementation guarantees incrementing timestamps among all
  client threads, provided that no more than 1000 are requested for a
  given clock tick (the exact granularity of of
  System.currentTimeMillis() depends on the operating system).

  If that rate is exceeded, a warning is logged and the timestamps don't
  increment anymore until the next clock tick. If you consistently
  exceed that rate, consider using
  ThreadLocalMonotonicTimestampGenerator."
  [^DriverContext driver-context]
  (AtomicTimestampGenerator. driver-context))

(defn server-side
  "A timestamp generator that always returns Long.MIN_VALUE, in order to let
  Cassandra assign server-side timestamps."
  [^DriverContext driver-context]
  (ServerSideTimestampGenerator. driver-context))

(defn thread-local
  "A timestamp generator based on System.currentTimeMillis(), with an
  incrementing thread-local counter to generate the sub-millisecond
  part.

  This implementation guarantees incrementing timestamps for a given
  client thread, provided that no more than 1000 are requested for a
  given clock tick (the exact granularity of of
  System.currentTimeMillis() depends on the operating system).

  If that rate is exceeded, a warning is logged and the timestamps don't
  increment anymore until the next clock tick."
  [^DriverContext driver-context]
  (ThreadLocalTimestampGenerator. driver-context))
