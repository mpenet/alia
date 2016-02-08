(ns qbits.alia.policy.load-balancing
  "The policy that decides which Cassandra hosts to contact for each new query."
  (:import
   (com.datastax.driver.core.policies
    DCAwareRoundRobinPolicy
    RoundRobinPolicy
    TokenAwarePolicy
    WhiteListPolicy)))

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
   (let [b (-> (DCAwareRoundRobinPolicy/builder))]
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
