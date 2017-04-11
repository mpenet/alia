(ns qbits.alia.policy.reconnection
  "Policy that decides how often the reconnection to a dead node is
  attempted."
  (:import
   (com.datastax.driver.core.policies
    ConstantReconnectionPolicy
    ExponentialReconnectionPolicy
    Policies)))

(defn constant-reconnection-policy
  "A reconnection policy that waits a constant time between each reconnection
  attempt."
  [constant-delay-ms]
  (ConstantReconnectionPolicy. constant-delay-ms))

(defn exponential-reconnection-policy
  "A reconnection policy that waits exponentially longer between each
  reconnection attempt (but keeps a constant delay once a maximum delay is
  reached)."
  [base-delay-ms max-delay-ms]
  (ExponentialReconnectionPolicy. base-delay-ms max-delay-ms))

(defmulti make (fn [policy] (or (:type policy) policy)))

(defn map->constant-reconnection-policy
  [{:keys [constant-delay-ms]}]
  (constant-reconnection-policy constant-delay-ms))

(defn map->exponential-reconnection-policy
  [{:keys [base-delay-ms max-delay-ms]}]
  (exponential-reconnection-policy base-delay-ms max-delay-ms))

(defmethod make :default
  [_]
  (Policies/defaultReconnectionPolicy))

(defmethod make :constant
  [policy]
  (map->constant-reconnection-policy policy))

(defmethod make :exponential
  [policy]
  (map->exponential-reconnection-policy policy))
