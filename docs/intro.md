# Alia

Alia's goal is to be a very simple to use library without trading
performance or features, compared to the java library it wraps.

This is outlined by the very low number of functions it exposes (a
dozen more or less) and the lack of very high level abstraction.
However alia comes with [Hayt](https://github.com/mpenet/alia/#hayt) a CQL
query DSL for clojure.

## Cluster initialisation

To get started you will need to prepare a cluster definition, so that
you can create sessions from it and interact with multiple keyspaces.

```clojure

(require '[qbits.alia :as alia] )

(def cluster (alia/cluster "localhost"))
```
`alia/cluster` can take a number of optional parameters:

ex:
```clojure
(def cluster (alia/cluster "localhost" :port 9999))

```
The following options are supported:

* `:contact-points`: a list of nodes ip addresses to connect to.

* `:port`: port to connect to on the nodes (native transport must be
  active on the nodes: `start_native_transport: true` in
  cassandra.yaml).

* `:load-balancing-policy`: Configure the [LoadBalancingPolicy](http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/LoadBalancingPolicy.html) to use for the new cluster.

* `:reconnection-policy`: Configure the [ReconnectionPolicy](http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/ReconnectionPolicy.html) to use for the new cluster.

* `:retry-policy`: Configure the
  [RetryPolicy](http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/policies/RetryPolicy.html)
  to use for the new cluster.

* `:metrics?`: Toggles metrics collection for the created cluster
  (metrics are enabled by default otherwise).

* `:auth-info`: Use the provided AuthInfoProvider to connect to
  Cassandra hosts. A clojure map that will be used to construct as
  SimpleAuthInfoProvider.

* `:compression`: Compression supported by the Cassandra binary
  protocol. Can be `:none` or `:snappy`.

* `:pooling-options`: The pooling options used by this builder.
   Supports the following
   ** :core-connections-per-host
   ** :max-connections-per-host
   ** :max-simultaneous-requests-per-connection
   ** :min-simultaneous-requests-per-connection

* `:pre-build-fn`: a function that will receive the cluster builder
  instance as only parameter when you want full control over
  initialisation. After its execution .build will be run, and the
  cluster will be initialised.


## Creating Sessions from a cluster instance

A session holds connections to a Cassandra cluster, allowing to query
it. Each session will maintain multiple connections to the cluster
nodes, and provides policies to choose which node to use for each
query (round-robin on all nodes of the cluster by default), handles
retries for failed query (when it makes sense), etc...

Session instances are thread-safe and usually a single instance is
enough per application. However, a given session can only be set to
one keyspace at a time, so one instance per keyspace is necessary.

```clojure
(def session (alia/connect cluster))
```
or if you want to use a particular keyspace from the start:

```clojure
(def session (alia/connect cluster "demokeyspace"))
```


## Executing queries

You can interact with C* using either raw queries or prepared statements.
There is a single function that allows you to do that: `alia/execute`.

This function supports a number of options, but the simplest example
of its use would look like this:

```clojure
(alia/execute session "SELECT * FROM foo;")
```

And it would return:

```clojure

 >> [[{:name "user_name", :value "frodo"}
       {:name "first_name", :value "Frodo"}
       {:name "last_name", :value "Baggins"}
       {:name "birth_year", :value 1}
       {:name "created" :value nil}
       {:name "valid" :value true}
       {:name "emails", :value #{"baggins@gmail.com" "f@baggins.com"}}
       {:name "amap", :value {"foo" 1 "bar" 2}}
       {:name "tags" :value [4 5 6]}
       {:name "auuid" :value #uuid "1f84b56b-5481-4ee4-8236-8a3831ee5892"}]]
```

Note: you can have the result transformed into a collection of maps
using `alia/rows->maps` on the result.

As you can see C* datatypes are translated to clojure friendly types
when it's possible: in this example `:emails` is a C* native Set,
`:tags` is a native list and `:amap` is a native map.

`alia/execute` has 2 distinct arity, you can skip the session parameter if
you have set it using `with-session` or `set-session!`:

ex:

```clojure
(alia/set-session! session)
(alia/execute "SELECT * FROM foo;")
;; ... more queries
```

or

```clojure
(alia/with-session session
  (alia/execute "SELECT * FROM foo;")
  ;; ... more queries)

```

For the next examples we will assume that the session has been set
using `set-session!` to be more succint.

### Asynchronous queries

The previous examples will block until a response is received from
cassandra. But it is possible to avoid that and perform them asynchronously.

There are a couple of ways to trigger this behavior.

The first one will just return a clojure promise (it will return
immediately) when you pass :async?  true, meaning you need
to use `clojure.core/deref` or `@` to get its value once it is
realized.

```clojure
(def query (alia/execute "SELECT * FROM foo;" :async? true))
;; ... more queries

;; and later to get its value

(do-something @query)
```

But in this example the deref operation is blocking, so this might not
be appropriate all the time.
A better way to run queries asynchronously is to provide callbacks to
the `execute` call:

```clojure
(alia/execute "SELECT * FROM foo;" :success (fn [rs] (do-something rs)))
```
Again it will return immediately (as a promise) and will trigger the
`:success` callback passing it the resultset once the result is
available. You can also provide an `:error` callback.


```clojure
(alia/execute "SELECT * FROM foo;"
              :success (fn [rs] (do-something rs))
              :error (fn [ex] (deal-with-the-error ex)))
```

### Prepared statements

Prepared statements still use `alia/execute`, but require 2 more
steps: one to prepare the query, and one to bind values to a prepared
statement.


In order to prepare a statement you need to use `alia/prepare`
```

```clojure
(def statement (alia/prepare "SELECT * FROM foo WHERE foo=? AND bar=?;"))
```

Again prepare expects a session as first parameter if you havent set
it globally or wrapped the call with `with-session`.

To bind values prior  to execution:

```clojure
(def bst (alia/bind statement ["value-of-foo" "value-of-bar"]))
```

You don't have to deal with translations of datatypes, this is
done under the hood.

Ok so now we are ready to execute the query with `alia/execute`, which
is used exactly the same way as explained earlier, there is nothing
specific to execution for prepared statements.

```clojure
(alia/execute bst)
```

And this is pretty much it for the core of the function you need to know.
There are a few other that can be usefull though:


### `alia/execute` advanced options

The complete signature of execute looks like this

```clojure
[session query & {:keys [async? success error executor
                          consistency routing-key retry-policy
                          tracing?]
                  :or {executor *executor*
                       consistency *consistency*}}]
```

`execute` supports a number of options I didn't mention earlier. you
can specify Consistency level, a custom ExecutorService, a retry
policy, a routing key and trigger tracing when calling it.

#### Consistency level

Here are the supported consistency levels:

`:all` `:any` `:each-quorum` `:local-quorum` `:one` `:quorum` `:three` `:two`


```clojure
(alia/execute bst :consistency :all)
```

This is one way of setting consistency, per query, but you can also
set its default or using a binding similar to what we showed earier
with sessions.

```clojure
(set-consistency! :three)
```

or


```clojure
(with-consistency :quorum
  .. execute a bunch of queries
)
```


#### Executors

The executor used to deal with resultset futures (in asynchronous
mode) can be passed as a named argument to `alia/execute`, or like
sessions, and consistency be set with a function globally or with a
binding:


```clojure
(alia/execute bst :executor executor-instance)
```


```clojure
(set-executor! executor-instance)
```

or


```clojure
(with-executor executor-instance
  .. execute a bunch of queries
)
```


#### Setting the query routing key

You can manually provide a routing key for this query. It is thus
optional since the routing key is only an hint for token aware load
balancing policy but is never mandatory.

RoutingKey on datastax doc : http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/SimpleStatement.html#setRoutingKey(java.nio.ByteBuffer...)

#### Retry Policy

Sets the retry policy to use for this query.
The default retry policy, if this option is not used, is the one
returned by Policies.getRetryPolicy() in the cluster
configuration. This method is thus only useful in case you want to
punctually override the default policy for this request.

RetryPolicy on datastax doc: (http://www.datastax.com/drivers/java/apidocs/com/datastax/driver/core/Query.html#setRetryPolicy(com.datastax.driver.core.policies.RetryPolicy)

## Shuting down

To clean up the resources used by alia once you are done, you can call
`alia/shutdown` on both/either the cluster and the session.

```clojure
(alia/shutdown session)
```
or

```clojure
(alia/shutdown cluster)
```

## Extending data type support

If you want alia to be able to encode custom datatypes without having
to do it yourself for every query you can extend the following
protocol `qbits.alia.codec/PCodec`'s `encode` function.

Here is an example that is provided for supporting joda-time, storing
them as longs:

```clojure
(ns qbits.alia.codec.joda-time
  (:require [qbits.alia.codec :as codec]))

(extend-protocol codec/PCodec
  org.joda.time.DateTime
  (encode [x]
    (.toDate x)))
```

## Hayt: Query DSL

Alia comes with the latest version of [Hayt](https://github.com/mpenet/hayt).
This is clojure a DSL, that is composable, very easy to use, performant
and provideds complete support for CQL3 features.

Some examples:

```clojure

(use 'qbits.hayt)

(select :foo
        (where {:bar 2}))

(update :some-table
         (set-columns {:bar 1
                       :baz [+ 2]})
         (where {:foo :bar
                 :moo [> 3]
                 :meh [:> 4]
                 :baz [:in [5 6 7]]})
         (order-by [:foo :asc]))
```

Queries are composable using `q->`

```clojure
(def base (select :foo (where {:foo 1})))

(q-> base
     (columns :bar :baz)
     (where {:bar 2})
     (order-by [:bar :asc])
     (using :ttl 10000))

```

To compile the queries just use `->raw` or `->prepared`

```clojure
(->raw (select :foo))
> "SELECT * FROM foo;"

(->prepared (select :foo (where {:bar 1})))
> ["SELECT * FROM foo WHERE bar=?;" [1]]

```

If you are curious about what else it can do head to the [very
incomplete documentation](http://mpenet.github.com/hayt/qbits.hayt.html) or the [tests](https://github.com/mpenet/hayt/blob/master/test/qbits/hayt/core_test.clj).

I will provide a complete documentation for it in the following days.
