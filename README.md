# alia
[![Build Status](https://secure.travis-ci.org/mpenet/alia.png?branch=master)](http://travis-ci.org/mpenet/alia)

> Coan-Teen, the female death spirit who walks without feet.

Cassandra CQL3 client for Clojure wrapping [datastax/java-driver 1.0.0](https://github.com/datastax/java-driver).

Alia's goal is to be a very simple to use library without trading
performance, features or exensibility.
It allows do to everything
[datastax/java-driver](https://github.com/datastax/java-driver) has to offer
with an idiomatic API, from a handfull of functions. The learning
curve or need to reach for the docs should be minimal.
Alia also integrates with [Hayt](#hayt-query-dsl), a CQL query DSL inspired
by korma/ClojureQL.

About datastax/java-driver:
It's built on top of the new binary protocol, can handle
pooling/balancing/failover/metrics, is very active, has synchronous and
asynchronous APIs, is likely to become the standard client for java
(it's the only one I know that uses the new protocol), and you can
trust [datastax](http://datastax.com/) to improve and maintain it.

If you want a Thrift based client for Clojure you could give a try to
[casyn](https://github.com/mpenet/casyn)

## Status

Alia is stable, and used in production by some users.
Its code doesn't do anything too exotic, so there shouldn't be any
surprises.

## Documentation

[A first draft can be found here](https://github.com/mpenet/alia/blob/master/docs/guide.md) and you can also consult the [codox generated documentation](http://mpenet.github.com/alia/#docs).

## Quickstart

Simple query execution using alia+hayt would look like this:

```clojure
(execute (select :users
                 (where {:name :foo})
                 (columns :bar "baz")))
```


But first things first: here is an example of a complete session using
raw queries.


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
                 1f84b56b-5481-4ee4-8236-8a3831ee5892, true);")

  (def prepared-statement (alia/prepare "select * from users where user_name=?;"))

  (alia/execute prepared-statement :values ["frodo"])

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

Asynchronous interface:

You need to use execute-async, which is used the same way as execute,
the return value is a
[result-channel](https://github.com/ztellman/lamina/wiki/Result-Channels) from
[Lamina](https://github.com/ztellman/lamina) (you can think of it as
an equivalent of a clojure.core/promise).

```clojure
(def result (alia/execute-async "select * from users;"))

```

To get the result once and wait for its realization we can dereference
it, a blocking operation.

```clojure
@result
```

Or we can use `success`/`error` handlers (it still returns a
`result-channel` just like before).

```clojure
(alia/execute-async "select * from users;"
                    :success (fn [rows] (do-something-with-result rows)
                    :error (fn [err] (print "fail!"))))

```

And it can do a lot more! Head to the
[docs](https://github.com/mpenet/alia/blob/master/docs/guide.md) or
the
[codox generated documentation](http://mpenet.github.com/alia/#docs).

## Hayt (Query DSL)

There is a nicer way to write your queries using
[Hayt](https://github.com/mpenet/hayt), this should be familiar if you
know Korma or ClojureQL.
One of the major difference is that Hayt doesn't use macros and just
generates maps, so if you need to compose clauses or queries together
you can just use the clojure.core functions that work on maps.

Some examples:

```clojure

(use 'qbits.hayt)

(select :foo (where {:bar 2}))

;; this generates a map
>> {:select :foo :where {:bar 2}}

(update :foo
         (set-columns {:bar 1
                       :baz [+ 2]})
         (where {:foo :bar
                 :moo [> 3]
                 :meh [:> 4]
                 :baz [:in [5 6 7]]}))


;; Composability using normal map manipulation functions

(def base (select :foo (where {:foo 1})))

(merge base
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

Alia supports hayt query direct execution, if you pass a non-compiled
query, it will be compiled and cached, this works for raw queries only atm,
on a LU cache with a threshold of 100 (the cache function is user
settable), so to be used carefully.

Ex:
```clojure
(execute (select :users (where {:name :foo})))
```

It covers everything that is possible with CQL3 (functions, handling
of collection types and their operations, ddl, prepared statements,
etc).
If you want to know more about it head to its [codox documentation](http://mpenet.github.com/hayt/codox/qbits.hayt.html) or
[Hayt's tests](https://github.com/mpenet/hayt/blob/master/test/qbits/hayt/core_test.clj).

## Installation

The binary protocol server is not started with the default
configuration file coming with Cassandra 1.2.
In the cassandra.yaml file, you need to set:

`start_native_transport: true`

Then add this to your dependencies:

```clojure
[cc.qbits/alia "1.1.0"]
```

Please check the
[Changelog](https://github.com/mpenet/alia/blob/master/CHANGELOG.md)
if you are upgrading.

## Mailing list

Alia has a
[mailing list](https://groups.google.com/forum/?fromgroups#!forum/alia-cassandra)
hosted on google groups.
Do not hesitate to ask your questions there.

## License

Copyright © 2013 [Max Penet](https://twitter.com/mpenet)

Distributed under the Eclipse Public License, the same as Clojure.
