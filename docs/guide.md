# Alia

Alia's goal is to be a very simple to use library without trading
performance, features or extensibility.

It allows do to everything
[datastax/java-driver](https://github.com/datastax/java-driver) has to offer
with an idiomatic API, from a handful of functions. The learning
curve or need to reach for the docs should be minimal.

Alia also comes with [Hayt](#hayt-query-dsl) a CQL query DSL inspired
by korma/ClojureQL.

## Cluster initialisation

The binary protocol server is not started with the default
configuration file coming with Cassandra 1.2. In the cassandra.yaml
file, you need to set:

`start_native_transport: true`

To get started you will need to prepare a cluster definition, so that
you can create sessions from it and interact with multiple keyspaces.

```clojure

(require '[qbits.alia :as alia] )

(def cluster (alia/cluster))
```
`alia/cluster` can take a number of optional parameters:

ex:
```clojure
(def cluster (alia/cluster {:contact-points ["192.168.1.30" "192.168.1.31" "192.168.1.32"]
                            :port 9042}))

```

The following options are supported:

* `:contact-points` : List of nodes ip addresses to connect to.

* `:port` : port to connect to on the nodes (native transport must be
  active on the nodes: `start_native_transport: true` in
  cassandra.yaml). Defaults to 9042 if not supplied.

* `:load-balancing-policy` : Configure the
  [Load Balancing Policy](http://mpenet.github.io/alia/qbits.alia.policy.load-balancing.html)
  to use for the new cluster.

* `:reconnection-policy` : Configure the
  [Reconnection Policy](http://mpenet.github.io/alia/qbits.alia.policy.reconnection.html)
  to use for the new cluster.

* `:retry-policy` : Configure the
  [Retry Policy](http://mpenet.github.io/alia/qbits.alia.policy.retry.html)
  to use for the new cluster.

* `:metrics?` : Toggles metrics collection for the created cluster
  (metrics are enabled by default otherwise).

* `:jmx-reporting?` : Toggles JMX reporting of the metrics.

* `:credentials` : Takes a map of :username and :password for use with
  Cassandra's PasswordAuthenticator

* `:compression` : Compression supported by the Cassandra binary
  protocol. Can be `:none` or `:snappy`.

* `:ssl?`: enables/disables SSL

* `:ssl-options` : advanced SSL setup using a
  `com.datastax.driver.core.SSLOptions` instance

* `:pooling-options` : The pooling options used by this builder.
  Options related to connection pooling.

  The driver uses connections in an asynchronous way. Meaning that
  multiple requests can be submitted on the same connection at the
  same time. This means that the driver only needs to maintain a
  relatively small number of connections to each Cassandra host. These
  options allow to control how many connections are kept exactly.

  For each host, the driver keeps a core amount of connections open at
  all time. If the utilisation of those connections reaches a
  configurable threshold ,more connections are created up to a
  configurable maximum number of connections.

  Once more than core connections have been created, connections in
  excess are reclaimed if the utilisation of opened connections drops
  below the configured threshold.

  Each of these parameters can be separately set for `:local` and `:remote`
  hosts (HostDistance). For `:ignored` hosts, the default for all those
  settings is 0 and cannot be changed.

  Each of the following configuration keys, take a map of {distance value}  :
  ex:
  ```clojure
  :core-connections-per-host {:remote 10 :local 100}
  ```

  + `:core-connections-per-host` Number
  + `:max-connections-per-host` Number
  + `:max-simultaneous-requests-per-connection` Number
  + `:min-simultaneous-requests-per-connection` Number

* `:socket-options`: a map of
  + `:connect-timeout-millis` Number
  + `:read-timeout-millis` Number
  + `:receive-buffer-size` Number
  + `:send-buffer-size` Number
  + `:so-linger` Number
  + `:tcp-no-delay?` Bool
  + `:reuse-address?` Bool
  + `:keep-alive?` Bool

* `:query-options`: a map of
  + `:fetch-size` Number
  + `:consistency` (consistency Keyword)
  + `:serial-consistency` (consistency Keyword)

* `:jmx-reporting?` Bool, enables/disables JMX reporting of the metrics.


The handling of these options is achieved with a multimethod that you
could extend if you need to handle some special case or want to create
your own options templates.
See `qbits.alia.cluster-options/set-cluster-option!` [[source]](../src/qbits/alia/cluster_options.clj#L19)

## Retry, Reconnection, Load balancing policies

These are all available from `qbits.alia.policy.*`.

Consult the codox documentation for details about these, they are
described in detail:

* [Load Balancing Policies](http://mpenet.github.io/alia/qbits.alia.policy.load-balancing.html)

* [Reconnection Policies](http://mpenet.github.io/alia/qbits.alia.policy.reconnection.html)

* [Retry Policies](http://mpenet.github.io/alia/qbits.alia.policy.retry.html)

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
There are three function that allow you to do that: `alia/execute`,
`alia/execute-async` and `alia/execute-chan`.

These functions support a number of options, but the simplest example
of its use would look like this:

```clojure
(alia/execute session "SELECT * FROM foo;")
```

And it would return:

```clojure

 >> ({:created nil,
      :last_name "Baggins",
      :emails #{"baggins@gmail.com" "f@baggins.com"},
      :tags [4 5 6],
      :first_name "Frodo",
      :amap {"foo" 1, "bar" 2},
      :auuid #uuid "1f84b56b-5481-4ee4-8236-8a3831ee5892",
      :valid true,
      :birth_year 1,
      :user_name "frodo"})
```

As you can see C* datatypes are translated to clojure friendly types
when it's possible: in this example `:emails` is a C* native Set,
`:tags` is a native list and `:amap` is a native map.

### Asynchronous queries

The previous examples will block until a response is received from
cassandra. But it is possible to avoid that and perform them asynchronously.

There are currently 2 interfaces for doing so. One using Lamina that
returns a result-channel (a promise) and additionaly an experimental
interface using `clojure/core.async` returning a clojure.core.async/chan.

#### Lamina asynchronous interface

You will need to use `execute-async` which returns
[result-channel](https://github.com/ztellman/lamina/wiki/Result-Channels)
from [Lamina](https://github.com/ztellman/lamina) (you can think of it
as a promise, it will return immediately), meaning you need to use
`clojure.core/deref` or `@` to get its value once it is realized (it's
one way to do it, more about this later). If an error happened, when
you deref the query it will throw the exception.

```clojure
(def query (alia/execute-async session "SELECT * FROM foo;"))
;; ... more queries

;; and later to get its value

(do-something @query)
```

But in this example the deref operation is blocking, so this might not
be appropriate all the time.
A better way to run queries asynchronously is to provide callbacks to
the `execute-async` call:

```clojure
(alia/execute-async session "SELECT * FROM foo;" :success (fn [rs] (do-something rs)))
```
Again it will return immediately (as a `result-channel`) and will
trigger the `:success` callback passing it the resultset once the
result is available. You can also provide an `:error` callback.


```clojure
(alia/execute-async session
                    "SELECT * FROM foo;"
                    {:success (fn [rs] (do-something rs))
                     :error (fn [ex] (deal-with-the-error ex))})
```

[Lamina](https://github.com/ztellman/lamina) makes it easy to work in
asynchronous situations, I encourage you to read about
[pipelines](https://github.com/ztellman/lamina/wiki/Pipelines-new) and
[channels](https://github.com/ztellman/lamina/wiki/Channels-new) in
particular, its API is really rich.


#### clojure/core.async asynchronous interface

`alia/execute-chan` has the same signature as the other execute
functions and as the name implies returns a clojure/core.async
channel that will contain a list of rows at some point or an exception instance.

Once you run it you have a couple of options to pull data from it.

+ using `clojure.core.async/take!` which takes the channel as first argument
and a callback as second:

```clojure
(take! (execute-chan session "select * from users;")
       (fn [rows-or-exception]
         (do-something rows)))
```

+ using `clojure.core.async/<!!` to block and pull the rows/exception
  from the channel.

```clojure
(def rows-or-exception (<!! (execute-chan session "select * from users;")))
```

+ using `clojure.core.async/go` block potentially using
  `clojure.core.async/alt!`.

```clojure
(go
  (loop [i 0 ret []]
    (if (= 3 i)
      ret
      (recur (inc i)
             (conj ret (<! (execute-chan session "select * from users limit 1")))))))
```

Some interesting examples are in the
[official walkthrough](https://github.com/clojure/core.async/blob/master/examples/walkthrough.clj)

This is experimental at this point and subject to changes.

### Prepared statements

Prepared statements still use `alia/execute` or `alia/execute-async`,
but require 1 (optionally 2) more steps.

In order to prepare a statement you need to use `alia/prepare`

```clojure
(def statement (alia/prepare session "SELECT * FROM foo WHERE foo=? AND bar=?;"))
```


```clojure
(alia/execute session statement {:values ["value-of-foo" "value-of-bar"]})
```

Alternatively you can bind values prior to execution (in case the
value don't change often and you don't want this step to be repeated at
query time for every call to `execute` or `execute-async`).

```clojure
(def bst (alia/bind statement ["value-of-foo" "value-of-bar"]))
(alia/execute session bst)
```

You don't have to deal with translations of data types, this is
done under the hood.

And this is it for the core of the function you need to know.
There are a few other that can be useful though.

### `alia/execute` & `alia/execute-async` advanced options

The complete signature of execute looks like this

`execute` and `execute-async` support a number of options I didn't
mention earlier, you can specify
* `:consistency` [Consistency](#consistency)
* `:retry-policy` [Retry Policy](http://mpenet.github.io/alia/qbits.alia.policy.retry.html)
* `:routing-key` [RoutingKey](#routing-key)
* `:tracing?` (boolean) triggers tracing (defaults to false)
* `:string-keys?` (boolean, defaults false) stringify keys (they are
   keywords by default, can be handy to prevent filling PermGen when
   dealing with compact storage "wide rows").
* `:fetch-size` (int) sets max number of rows returned from server at a time.

Additionally `execute-async` accepts
an `:executor` option that will set the java.util.concurrent
`ExecutorService` instance to be used for the ResultFuture (see:
[Executors](#executors)).

#### Consistency

Here are the supported consistency and serial-consistency levels:

`:all` `:any` `:each-quorum` `:local-quorum` `:one` `:quorum` `:three` `:two`


```clojure
(alia/execute session bst {:consistency :all})
```

You can also set the consistency globaly at the cluster level via
`qbits.alia/cluster` options.

#### Executors

The executor used to deal with result set futures (in asynchronous
mode) can be passed as a named argument to `alia/execute-async`, or like
sessions, and consistency be set with a function globally or with a
binding:


```clojure
(alia/execute-async session bst {:executor executor-instance})
```
Alia comes with a thin wrapper for j.u.c Executors, [mpenet/knit](https://github.com/mpenet/knit).

Ex:
```clojure
(require '[qbits.knit :as knit])
(alia/execute-async session bst {:executor (knit/executor :fixed :num-threads 50)})
```

#### Routing key

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

[Retry Policies](http://mpenet.github.io/alia/qbits.alia.policy.retry.html)

## Shutting down

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

If you want alia to be able to encode custom data types without having
to do it yourself for every query you can extend the following
protocol `qbits.alia.codec/PCodec`'s `encode` function.

Here is an example that is provided for supporting joda-time.

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
This is a query DSL, that is composable, very easy to use, performant
and provides complete support for CQL3 features.

See [codox documentation](http://mpenet.github.com/hayt/codox/qbits.hayt.html) or
[Hayt's tests](https://github.com/mpenet/hayt/blob/master/test/qbits/hayt/core_test.clj).

Alia supports Hayt query direct execution, if you pass a non-compiled
query, it will be compiled and cached.

The same is true with `prepare`, the query gets prepared (ignoring the
values, it's for convenience really, it compiles the query under the
hood and only passes the parameterized string with ? placeholders).
```clojure
(prepare session (select :user (where {:foo "bar"})))
(prepare session (select :user (where {:bar ?})))
```

Ex:
```clojure
(execute session (select :users (where {:name :foo})))
```
You can have control over the query caching using
`set-hayt-query-fn!` and provide your own memoize implementation or you
can set its value to `qbits.hayt/->raw` if you prefer not to use query caching.
The default uses `clojure.core.memoize` with a LU cache with a `:threshold`
of 100.
