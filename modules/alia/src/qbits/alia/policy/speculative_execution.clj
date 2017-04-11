(ns qbits.alia.policy.speculative-execution
  (:require [qbits.alia.enum :as enum])
  (:import
   (com.datastax.driver.core
    PerHostPercentileTracker
    ClusterWidePercentileTracker
    PercentileTracker$Builder)
   (com.datastax.driver.core.policies
    ConstantSpeculativeExecutionPolicy
    NoSpeculativeExecutionPolicy
    PercentileSpeculativeExecutionPolicy
    Policies)))

(defn constant-speculative-execution-policy
  "A SpeculativeExecutionPolicy that schedules a given number of
  speculative executions, separated by a fixed delay."
  [constant-delay-ms max-speculative-executions]
  (ConstantSpeculativeExecutionPolicy. (int constant-delay-ms)
                                       (int max-speculative-executions)))

(defn percentile-speculative-execution-policy
  "A policy that triggers speculative executions when the request to
  the current host is above a given percentile. This class uses a
  PerHostPercentileTracker that must be registered with the cluster
  instance"
  [percentile-tracker percentile max-speculative-executions]
  (PercentileSpeculativeExecutionPolicy. ^PerHostPercentileTracker percentile-tracker
                                         (double percentile)
                                         (int max-speculative-executions)))

(defn no-speculative-execution-policy
  "A SpeculativeExecutionPolicy that never schedules speculative executions."
  []
  (NoSpeculativeExecutionPolicy/INSTANCE))

(defn percentile-tracker
  "A `LatencyTracker` that records query latencies over a sliding time interval,
  and exposes an API to retrieve the latency at a given percentile.

  It uses `HdrHistogram` to record latencies: for each category, there is a
  \"live\" histogram where current latencies are recorded, and a \"cached\",
  read-only histogram that is used when clients call
  `getLatencyAtPercentile(Host, Statement, Exception, double)`. Each time
  the cached histogram becomes older than the interval, the two histograms
  are switched. Statistics will not be available during the first interval
  at cluster startup, since we don't have a cached histogram yet."
  [^PercentileTracker$Builder builder
   {:keys [interval min-recorded-values significant-value-digits]}]
  (let [[interval-value interval-unit] interval]
    (when interval
      (.withInterval ^PercentileTracker$Builder builder
                     ^long (long interval-value)
                     ^TimeUnit (enum/time-unit interval-unit)))
    (when min-recorded-values
      (.withMinRecordedValues ^PercentileTracker$Builder builder
                              ^int (int min-recorded-values)))
    (when significant-value-digits
      (.withNumberOfSignificantValueDigits ^PercentileTracker$Builder builder
                                           ^int (int significant-value-digits)))
    (.build ^PercentileTracker$Builder builder)))

(defn cluster-wide-percentile-tracker
  "A `PercentileTracker` that aggregates all measurements into a single
  histogram.

  This gives you global latency percentiles for the whole cluster, meaning that
  latencies of slower hosts will tend to appear in higher percentiles."
  [{:keys [highest-trackable-latency-millis] :as opts}]
  (percentile-tracker
   (ClusterWidePercentileTracker/builder highest-trackable-latency-millis)
   opts))

(defn per-host-percentile-tracker
  "A `PercentileTracker` that maintains a separate histogram for each host.

  This gives you per-host latency percentiles, meaning that each host will
  only be compared to itself."
  [{:keys [highest-trackable-latency-millis] :as opts}]
  (percentile-tracker
   (PerHostPercentileTracker/builder highest-trackable-latency-millis)
   opts))

(defmulti make (fn [policy] (or (:type policy) policy)))

(defn map->constant-speculative-execution-policy
  [{:keys [constant-delay-millis max-speculative-executions]}]
  (constant-speculative-execution-policy constant-delay-millis
                                         max-speculative-executions))

(defn map->percentile-speculative-execution-policy
  [tracker {:keys [percentile max-executions]}]
  (percentile-speculative-execution-policy tracker
                                           percentile
                                           max-executions))

(defmethod make :default
  [_]
  (Policies/defaultSpeculativeExecutionPolicy))

(defmethod make :none
  [_]
  (no-speculative-execution-policy))

(defmethod make :constant
  [policy]
  (map->constant-speculative-execution-policy policy))

(defmethod make :cluster-wide-percentile-tracker
  [policy]
  (map->percentile-speculative-execution-policy
   (cluster-wide-percentile-tracker policy)
   policy))

(defmethod make :per-host-percentile-tracker
  [policy]
  (map->percentile-speculative-execution-policy
   (per-host-percentile-tracker policy)
   policy))
