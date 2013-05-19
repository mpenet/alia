(ns qbits.alia.policy.retry
  "A policy that defines a default behavior to adopt when a request returns a
TimeoutException or an UnavailableException. Such policy allows to centralize
the handling of query retries, allowing to minimize the need for exception
catching/handling in business code."
  (:require [qbits.alia.utils :as utils])
  (:import (com.datastax.driver.core.policies
            DefaultRetryPolicy
            DowngradingConsistencyRetryPolicy
            FallthroughRetryPolicy
            LoggingRetryPolicy)
           (com.datastax.driver.core WriteType)))

(def write-types (utils/enum-values->map (WriteType/values)))

(def default-retry-policy
  "The default retry policy.

This policy retries queries in only two cases:

On a read timeout, if enough replica replied but data was not
retrieved.  On a write timeout, if we timeout while writing the
distributed log used by batch statements.

This retry policy is conservative in that it will never retry with a
different consistency level than the one of the initial operation.

In some cases, it may be convenient to use a more aggressive retry
policy like `downgrading-consistency-retry-policy`

http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/DefaultRetryPolicy.html"
  (constantly (DefaultRetryPolicy/INSTANCE)))

(def fallthrough-retry-policy
  "A retry policy that never retry (nor ignore).

All of the methods of this retry policy unconditionally return
RetryPolicy.RetryDecision.rethrow(). If this policy is used, retry
will have to be implemented in business code.

http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/FallthroughRetryPolicy.html"
  (constantly (FallthroughRetryPolicy/INSTANCE)))

(def downgrading-consistency-retry-policy
  "A retry policy that sometimes retry with a lower consistency level
than the one initially requested.

BEWARE: This policy may retry queries using a lower consistency level
than the one initially requested. By doing so, it may break
consistency guarantees. In other words, if you use this retry policy,
there is cases (documented below) where a read at QUORUM may not see a
preceding write at QUORUM. Do not use this policy unless you have
understood the cases where this can happen and are ok with that. It is
also highly recommended to always wrap this policy into
LoggingRetryPolicy to log the occurrences of such consistency break.

This policy implements the same retries than the DefaultRetryPolicy
policy. But on top of that, it also retries in the following cases:

On a read timeout: if the number of replica that responded is greater
than one but lower than is required by the requested consistency
level, the operation is retried at a lower consistency level.  On a
write timeout: if the operation is an WriteType.UNLOGGED_BATCH and at
least one replica acknowledged the write, the operation is retried at
a lower consistency level.

Furthermore, for other operation, if at least one replica acknowledged
the write, the timeout is ignored.  On an unavailable exception: if at
least one replica is alive, the operation is retried at a lower
consistency level.  The reasoning being this retry policy is the
following one. If, based on the information the Cassandra coordinator
node returns, retrying the operation with the initially requested
consistency has a change to succeed, do it. Otherwise, if based on
these information we know the initially requested consistency level
cannot be achieve currently, then:

For writes, ignore the exception (thus silently failing the
consistency requirement) if we know the write has been persisted on at
least one replica.


For reads, try reading at a lower consistency level (thus silently
failing the consistency requirement).  In other words, this policy
implements the idea that if the requested consistency level cannot be
achieved, the next best thing for writes is to make sure the data is
persisted, and that reading something is better than reading nothing,
even if there is a risk of reading stale data.

http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/DowngradingConsistencyRetryPolicy.html
"
  (constantly (DowngradingConsistencyRetryPolicy/INSTANCE)))

(defn logging-retry-policy
  "A retry policy that wraps another policy, logging the decision made by its
sub-policy.

Note that this policy only log the IGNORE and RETRY decisions (since
RETHROW decisions are just meant to propagate the cassandra
exception).

The logging is done at the INFO level.

http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/LoggingRetryPolicy.html"
  [retry-policy]
  (LoggingRetryPolicy. retry-policy))

(defn on-read-timeout
  "Defines whether to retry and at which consistency level on a read timeout."
  [^RetryPolicy policy cl required-responses received-responses data-retrieved?
   nb-retry]
  (doto policy
    (.onReadTimeout query
                    cl
                    (int required-responses)
                    (int received-responses)
                    data-retrieved?
                    (int nb-retry))))

(defn on-unavailable
  "Defines whether to retry and at which consistency level on an unavailable
exception."
  [^RetryPolicy policy query cl required-responses required-replica
   alive-responses nb-retry]
  (doto policy
    (.onReadTimeout query
                    cl
                    (int required-replica)
                    (int alive-responses)
                    (int nb-retry))))

(defn on-write-timeout
  "Defines whether to retry and at which consistency level on a write timeout.
write-type accepts a write-type keyword or a WriteType instance"
  [^RetryPolicy policy query cl write-type required-acks received-acks nb-retry]
  (doto policy
    (.onReadTimeout cl
                    (get write-types write-type write-type)
                    (int required-acks)
                    (int received-acks)
                    (int nb-retry))))
