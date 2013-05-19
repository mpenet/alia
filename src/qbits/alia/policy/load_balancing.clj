(ns qbits.alia.policy.load-balancing
  (:import
   (com.datastax.driver.core.policies
    DCAwareRoundRobinPolicy
    RoundRobinPolicy
    TokenAwarePolicy)))

(defn round-robin
  []
  (RoundRobinPolicy.))

(defn token-aware
  [child]
  (TokenAwarePolicy. child))

(defn dc-aware-round-robin
  ([dc used-hosts-per-remote-dc]
     (DCAwareRoundRobinPolicy. dc used-hosts-per-remote-dc))
  ([dc]
     (DCAwareRoundRobinPolicy. dc)))
