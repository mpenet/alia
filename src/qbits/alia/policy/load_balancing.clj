(ns qbits.alia.policy.load-balancing
  (:import
   (com.datastax.driver.core.policies
    DCAwareRoundRobinPolicy
    RoundRobinPolicy
    TokenAwarePolicy)))

(defn round-robin
"A Round-robin load balancing policy.

This policy queries nodes in a round-robin fashion. For a given query,
if an host fail, the next one (following the round-robin order) is
tried, until all hosts have been tried.

This policy is not datacenter aware and will include every known
Cassandra host in its round robin algorithm. If you use multiple
datacenter this will be inefficient and you will want to use the
`dc-aware-round-robin` load balancing policy instead.

http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/RoundRobinPolicy.html"
  []
  (RoundRobinPolicy.))

(defn token-aware
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

(defn dc-aware-round-robin
    "A data-center aware Round-robin load balancing policy.

This policy provides round-robin queries over the node of the local
datacenter. It also includes in the query plans returned a
configurable number of hosts in the remote datacenters, but those are
always tried after the local nodes. In other words, this policy
guarantees that no host in a remote datacenter will be queried unless
no host in the local datacenter can be reached.

If used with a single datacenter, this policy is equivalent to the
LoadBalancingPolicy.RoundRobin policy, but its DC awareness incurs a
slight overhead so the `round-robin` policy could
be prefered to this policy in that case.

http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/DCAwareRoundRobinPolicy.html"
  ([dc used-hosts-per-remote-dc]
     (DCAwareRoundRobinPolicy. dc used-hosts-per-remote-dc))
  ([dc]
     (DCAwareRoundRobinPolicy. dc)))
