# alia
<!-- [![Build Status](https://secure.travis-ci.org/mpenet/alia.png?branch=master)](http://travis-ci.org/mpenet/alia) -->

> Coan-Teen, the female death spirit who walks without feet.

Cassandra CQL3 client for Clojure wrapping [datastax/java-driver](https://github.com/datastax/java-driver).

Alia's goal is to be a very simple to use library without trading
performance, features or exensibility.
It allows do to everything
[datastax/java-driver](https://github.com/datastax/java-driver) has to offer
with an idiomatic API, from a handfull of functions. The learning
curve or need to reach for the docs should be minimal.
Alia also comes with [Hayt](#hayt-query-dsl) a CQL query DSL inspired
by korma/ClojureQL.

About datastax/java-driver:
It's built on top of the new binary protocol, can handle
pooling/balancing/failover/metrics, is very active, has synchronous and
asynchronous APIs, is likely to become the standard client for java
(it's the only one I know that uses the new protocol) and you can
trust [datastax](http://datastax.com/) people to improve and maintain
it.

If you want a Thrift based client for Clojure you could give a try to
[casyn](https://github.com/mpenet/casyn)

## What Alia can do

* Nice simple and extensible api to work with string queries or
  prepared statements, synchronous/asynchronous execution, using
  promises or success/error callbacks depending on the mode you
  choose,, with transparent handling of clojure datatypes, all
  cassandra data types are supported.


* The exposed parts of the public api all allow to extend it to fit
  your needs and leverage all the good stuff available from
  [datastax/java-driver](https://github.com/datastax/java-driver).

## Documentation

[A first draft can be found here](https://github.com/mpenet/alia/blob/master/docs/intro.md) and you can also consult the [codox generated documentation](http://mpenet.github.com/alia/#docs).

## Show me some code!

This is an example of a complete session.

```clojure
(require '[qbits.alia :as alia] )

(def cluster (alia/cluster "localhost"))
```

Sessions are separate so that you can interact with multiple
keyspaces from the same cluster definition(

```clojure
(def session (alia/connect cluster))

(alia/execute session "CREATE KEYSPACE alia
                       WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 3};")
```

Every function that requires session as first argument can be also
used without this argument if you provide a binding or set it globally (valid for
alia/execute and alia/prepare) using `with-session` or `set-session!`:

```clojure
(alia/with-session session
   (alia/execute "USE alia;")
   (alia/execute "CREATE TABLE users (user_name varchar,
                                     first_name varchar,
                                     last_name varchar,
                                     auuid uuid,
                                     birth_year bigint,
                                     created timestamp,
                                     valid boolean,
                                     emails set<text>,
                                     tags list<bigint>,
                                     amap map<varchar, bigint>,
                                     PRIMARY KEY (user_name));")
  (alia/execute "INSERT INTO users
                 (user_name, first_name, last_name, emails, birth_year, amap, tags, auuid,valid)
                 VALUES('frodo', 'Frodo', 'Baggins',
                 {'f@baggins.com', 'baggins@gmail.com'}, 1,
                 {'foo': 1, 'bar': 2}, [4, 5, 6],
                 '1f84b56b-5481-4ee4-8236-8a3831ee5892', true);")

  (def prepared-statement (alia/prepare "select * from users where user_name=?;"))

  (alia/execute prepared-statement :values ["frodo"])

  >> ({"created" nil,
       "last_name" "Baggins",
       "emails" #{"baggins@gmail.com" "f@baggins.com"},
       "tags" [4 5 6],
       "first_name" "Frodo",
       "amap" {"foo" 1, "bar" 2},
       "auuid" #uuid "1f84b56b-5481-4ee4-8236-8a3831ee5892",
       "valid" true,
       "birth_year" 1,
       "user_name" "frodo"})
```

Asynchronous interface:

You need to use execute-async, which is used the same way as execute,
the return value is a
[promise](http://clojuredocs.org/clojure_core/clojure.core/promise)
(non blocking).

```clojure
(def result (alia/execute-async "select * from users;"))

```

To get the result once and wait for its realization we can dereference
it, a blocking operation.

```clojure
@result
```

Or we can use `success`/`error` handlers (it still returns a promise
just like before)

```clojure
(alia/execute-async "select * from users;"
                    :success (fn [r] (do-something-with-result r)
                    :error (fn [e] (print "fail!"))))

```

And it can do a tons more! Head to the
[docs](https://github.com/mpenet/alia/blob/master/docs/intro.md) or
the
[codox generated documentation](http://mpenet.github.com/alia/#docs).

## Hayt (Query DSL)

There is a nicer way to write your queries using
[Hayt](https://github.com/mpenet/hayt), this should be familiar if you
know Korma or ClojureQL.
One of the major difference is that Hayt doesn't use macros.

Some examples:

```clojure

(use 'qbits.hayt)

(select :foo (where {:bar 2}))

(update :foo
         (set-columns {:bar 1
                       :baz [+ 2]})
         (where {:foo :bar
                 :moo [> 3]
                 :meh [:> 4]
                 :baz [:in [5 6 7]]}))


;; They are composable using q->
(def base (select :foo (where {:foo 1})))

(q-> base
     (columns :bar :baz)
     (where {:bar 2})
     (order-by [:bar :asc])
     (using :ttl 10000))

;; To compile the queries just use ->raw or ->prepared

(->raw (select :foo))
> "SELECT * FROM foo;"

(->prepared (select :foo (where {:bar 1})))
> ["SELECT * FROM foo WHERE bar=?;" [1]]


```

It is still in developement but it covers everything that is possible
with CQL3 (functions, handling of collection types and their
operations, ddl, prepared statements, etc).
Proper documentation will come soon, if you want to know more about it head to
[Hayt's tests](https://github.com/mpenet/hayt/blob/master/test/qbits/hayt/core_test.clj).

## Installation

You need to install
[datastax/java-driver](https://github.com/datastax/java-driver)
manually until it gets publised (it is still in developpement).

```bash
git clone git@github.com:datastax/java-driver.git
cd java-driver/driver-core
mvn install -DskipTests
```

The binary protocol server is not started with the default configuration file coming with Cassandra 1.2. In the cassandra.yaml file, you need to set:

`start_native_transport: true`

Then add this to your dependencies:

```clojure
[cc.qbits/alia "0.3.0"]
```

Please check the
[Changelog](https://github.com/mpenet/alia/blob/master/CHANGELOG.md)
if you are upgrading, I might introduce breaking changes before it
reaches 1.0 (until java-driver is published).

## License

Copyright © 2013 Max Penet

Distributed under the Eclipse Public License, the same as Clojure.
