(ns qbits.alia.policy.reconnection
  (:import
   (com.datastax.driver.core.policies
    ConstantReconnectionPolicy
    ExponentialReconnectionPolicy)))

(defn constant
  [constant-delay-ms]
  (ConstantReconnectionPolicy. constant-delay-ms))

(defn exponential
  [base-delay-ms max-delay-ms]
  (ExponentialReconnectionPolicy. base-delay-ms max-delay-ms))
