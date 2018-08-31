(ns qbits.alia.policy.load-balancing
  "The policy that decides which Cassandra hosts to contact for each new query."
  (:require [qbits.alia.enum :as enum])
  (:import
   (com.datastax.driver.core.policies
    DCAwareRoundRobinPolicy
    RoundRobinPolicy
    TokenAwarePolicy
    WhiteListPolicy
    LatencyAwarePolicy
    Policies)
   (java.net
    InetSocketAddress
    InetAddress)))

(defn round-robin-policy
  "A Round-robin load balancing policy.

  This policy queries nodes in a round-robin fashion. For a given query,
  if an host fail, the next one (following the round-robin order) is
  tried, until all hosts have been tried.

  This policy is not datacenter aware and will include every known
  Cassandra host in its round robin algorithm. If you use multiple
  datacenter this will be inefficient and you will want to use the
  `dc-aware-round-robin-policy` load balancing policy instead.

  http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/RoundRobinPolicy.html"
  []
  (RoundRobinPolicy.))

(defn token-aware-policy
  "A wrapper load balancing policy that add token awareness to a child policy.

  This policy encapsulates another policy. The resulting policy works in
  the following way:

  the distance method is inherited from the child policy.  the iterator
  return by the newQueryPlan method will first return the LOCAL replicas
  for the query (based on Query.getRoutingKey()) if possible (i.e. if
  the query getRoutingKey method doesn't return null and if
  Metadata.getReplicas(java.nio.ByteBuffer) returns a non empty set of
  replicas for that partition key). If no local replica can be either
  found or successfully contacted, the rest of the query plan will
  fallback to one of the child policy.  Do note that only replica for
  which the child policy distance method returns HostDistance.LOCAL will
  be considered having priority. For example, if you wrap
  DCAwareRoundRobinPolicy with this token aware policy, replicas from
  remote data centers may only be returned after all the host of the
  local data center.

  http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/TokenAwarePolicy.html"
  [child]
  (TokenAwarePolicy. child))

(defn dc-aware-round-robin-policy
  "A data-center aware Round-robin load balancing policy.

  This policy provides round-robin queries over the node of the local
  datacenter. It also includes in the query plans returned a
  configurable number of hosts in the remote datacenters, but those are
  always tried after the local nodes. In other words, this policy
  guarantees that no host in a remote datacenter will be queried unless
  no host in the local datacenter can be reached.

  If used with a single datacenter, this policy is equivalent to the
  LoadBalancingPolicy.RoundRobin policy, but its DC awareness incurs a
  slight overhead so the `round-robin-policy` policy could
  be prefered to this policy in that case.

  http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/DCAwareRoundRobinPolicy.html"
  ([dc used-hosts-per-remote-dc]
   (let [b (DCAwareRoundRobinPolicy/builder)]
     (.withLocalDc b dc)
     (when used-hosts-per-remote-dc
       (.withUsedHostsPerRemoteDc b (int used-hosts-per-remote-dc)))
     (.build b)))
  ([dc] (dc-aware-round-robin-policy dc nil)))

(defn whitelist-policy
  "A load balancing policy wrapper that ensure that only hosts from a
  provided white list will ever be returned.

  This policy wraps another load balancing policy and will delegate
  the choice of hosts to the wrapped policy with the exception that
  only hosts contained in the white list provided when constructing
  this policy will ever be returned. Any host not in the while list
  will be considered {@code IGNORED} and thus will not be connected
  to.

  This policy can be useful to ensure that the driver only connects to
  a predefined set of hosts. Keep in mind however that this policy
  defeats somewhat the host auto-detection of the driver. As such,
  this policy is only useful in a few special cases or for testing,
  but is not optimal in general.  If all you want to do is limiting
  connections to hosts of the local data-center then you should use
  DCAwareRoundRobinPolicy and not this policy in particular."
  [child whitelist-coll]
  (WhiteListPolicy. child whitelist-coll))

(defn latency-aware-balance-policy
  "A wrapper load balancing policy that adds latency awareness to a
  child policy.

  When used, this policy will collect the latencies of the queries to
  each Cassandra node and maintain a per-node latency score
  (an average). Based on these scores, the policy will penalize
  (technically, it will ignore them unless no other nodes are up) the
  nodes that are slower than the best performing node by more than
  some configurable amount (the exclusion threshold).

  The latency score for a given node is a based on a form of
  exponential moving average. In other words, the latency score of a
  node is the average of its previously measured latencies, but where
  older measurements gets an exponentially decreasing weight. The
  exact weight applied to a newly received latency is based on the
  time elapsed since the previous measure (to account for the fact
  that latencies are not necessarily reported with equal regularity,
  neither over time nor between different nodes).

  Once a node is excluded from query plans (because its averaged
  latency grew over the exclusion threshold), its latency score will
  not be updated anymore (since it is not queried). To give a chance
  to this node to recover, the policy has a configurable retry period.
  The policy will not penalize a host for which no measurement has
  been collected for more than this retry period."
  [child {:keys [exclusion-threshold min-measure retry-period scale update-rate]}]
  (let [[retry-period-value retry-period-unit] retry-period
        [scale-value scale-unit] scale
        [update-rate-value update-rate-unit] update-rate
        builder (LatencyAwarePolicy/builder child)]
    (when exclusion-threshold
      (.withExclusionThreshold builder
                               (double exclusion-threshold)))
    (when min-measure
      (.withMininumMeasurements builder
                                (int min-measure)))
    (when retry-period
      (.withRetryPeriod builder
                        (long retry-period-value)
                        (enum/time-unit retry-period-unit)))
    (when  scale
      (.withScale builder
                  (long scale-value)
                  (enum/time-unit scale-unit)))
    (when update-rate
      (.withUpdateRate builder
                       (long update-rate-value)
                       (enum/time-unit update-rate-unit)))
    (.build builder)))

(defn socket-address
  [{:keys [ip hostname port]}]
  (cond
    (and hostname port) (InetSocketAddress. ^String hostname
                                            (int port))
    (and ip port) (InetSocketAddress. (InetAddress/getByName ip)
                                      (int port))
    port (InetSocketAddress. (int port))))


(defmulti make (fn [policy] (or (:type policy) policy)))

(defn map->whitelist-policy
  [{:keys [child white-list]}]
  (whitelist-policy (make child)
                    (map socket-address white-list)))

(defn map->dc-aware-round-robin-policy
  [{:keys [data-centre used-hosts-per-remote-dc]}]
  (dc-aware-round-robin-policy data-centre used-hosts-per-remote-dc))

(defmethod make :default
  [_]
  (Policies/defaultLoadBalancingPolicy))

(defmethod make :round-robin
  [_]
  (round-robin-policy))

(defmethod make :whitelist
  [policy]
  (map->whitelist-policy policy))

(defmethod make :dc-aware-round-robin
  [policy]
  (dc-aware-round-robin-policy policy))

(defmethod make :token-aware/round-robin
  [_]
  (token-aware-policy (round-robin-policy)))

(defmethod make :token-aware/whitelist
  [policy]
  (token-aware-policy (map->whitelist-policy policy)))

(defmethod make :token-aware/dc-aware-round-robin
  [policy]
  (token-aware-policy (map->dc-aware-round-robin-policy policy)))

(defmethod make :latency-aware/round-robin
  [policy]
  (latency-aware-balance-policy (round-robin-policy) policy))

(defmethod make :latency-aware/whitelist
  [policy]
  (latency-aware-balance-policy (map->whitelist-policy policy) policy))

(defmethod make :latency-aware/dc-aware-round-robin
  [policy]
  (latency-aware-balance-policy (map->dc-aware-round-robin-policy policy) policy))
