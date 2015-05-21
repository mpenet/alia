(ns qbits.alia.policy.speculative-execution
  (:import
   (com.datastax.driver.core PerHostPercentileTracker)
   (com.datastax.driver.core.policies
    ConstantSpeculativeExecutionPolicy
    NoSpeculativeExecutionPolicy
    PercentileSpeculativeExecutionPolicy)))

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
