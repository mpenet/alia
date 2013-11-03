# alia
[![Build Status](https://secure.travis-ci.org/mpenet/alia.png?branch=master)](http://travis-ci.org/mpenet/alia)

> Coan-Teen, the female death spirit who walks without feet.

Cassandra CQL3 client for Clojure wrapping [datastax/java-driver](https://github.com/datastax/java-driver).

## What's in the box?

* Built on an **extremely solid base**,
  [datastax/java-driver](https://github.com/datastax/java-driver),
  based on the new **CQL native protocol**
* **Simple API** with a minimal learning curve
* **Great performance**
* Provides a **versatile CQL3+ DSL**, [Hayt](#hayt-query-dsl)
* Support for **Raw queries**, **Prepared Statements** or **[Hayt](#hayt-query-dsl) queries**
* Can do both **Synchronous and Asynchronous** query execution, using
  **Lamina** or an experimental **clojure/core.async** interface.
* Support for **all of
  [datastax/java-driver](https://github.com/datastax/java-driver)
  advanced options**: jmx, auth, SSL, compression, consistency, custom
  executors, custom routing and more
* Support and sugar for **query tracing**, **metrics**, **retry
  policies**, **load balancing policies**, **reconnection policies**
  and **UUIDs** generation
* Extensible **Clojure data types support** & **clojure.core/ex-data**
  integration
* **Lazy and potentialy chunked sequences over queries**

## Installation

The binary protocol server is not started with the default
configuration file coming with Cassandra 1.2+
In the cassandra.yaml file, you need to set:

`start_native_transport: true`

Then add this to your dependencies:

```clojure
[cc.qbits/alia "1.10.2"]
```

Please check the
[Changelog](https://github.com/mpenet/alia/blob/master/CHANGELOG.md)
if you are upgrading.

## Documentation

[A guide](https://github.com/mpenet/alia/blob/master/docs/guide.md) is
available and you can also consult the
[codox generated documentation](http://mpenet.github.com/alia/#docs).

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
keyspaces from the same cluster definition.

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

### Asynchronous interfaces:

There are currently 2 ways to use the asynchronous methods of the
underlying driver, using a **Lamina** based api or the newly released
**clojure/core.async**

#### Async using the Lamina based api

Using `execute-async` which is used the same way as execute,
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

#### Async using clojure/core.async


`alia/execute-chan` has the same signature as the other execute
functions and as the name implies returns a clojure/core.async
channel that will contain a list of rows at some point or an exception
instance.

Once you run it you have a couple of options to pull data from it.

+ using `clojure.core.async/take!` which takes the channel as first argument
and a callback as second:

```clojure
(take! (execute-chan  "select * from users;")
       (fn [rows-or-exception]
         (do-something rows)))
```

+ using `clojure.core.async/<!!` to block and pull the rows/exception
  from the channel.

```clojure
(def rows-or-exception (<!! (execute-chan "select * from users;")))
```

+ using `clojure.core.async/go` block, and potentially using
  `clojure.core.async/alt!`.

```clojure
(go
 (loop [;;the list of queries remaining
        queries [(alia/execute-chan (select :foo))
                 (alia/execute-chan (select :bar))
                 (alia/execute-chan (select :baz))]
        ;; where we'll store our results
        query-results '()]
   ;; If we are done with our queries return them, it's the exit clause
   (if (empty? queries)
     query-results
     ;; else wait for one query to complete (first completed first served)
     (let [[result channel] (alts!! queries)]
       (println "Received result: "  result " from channel: " channel)
       (recur
        ;; we remove the channel that just completed from our
        ;; pending queries collection
        (remove #{channel} queries)

        ;; and finally we add the result of this query to our
        ;; bag of results
        (conj query-results result))))))
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
query to `execute` or `execute-async`, it will be compiled and cached on a LU cache with a threshold of
100 (the cache function is user settable), so to be used carefully. The same is true with `prepare`.

Ex:
```clojure
(execute (select :users (where {:name :foo})))
```

It covers everything that is possible with CQL3 (functions, handling
of collection types and their operations, ddl, prepared statements,
etc).
If you want to know more about it head to its [codox documentation](http://mpenet.github.com/hayt/codox/qbits.hayt.html) or
[Hayt's tests](https://github.com/mpenet/hayt/blob/master/test/qbits/hayt/core_test.clj).

## Mailing list

Alia has a
[mailing list](https://groups.google.com/forum/?fromgroups#!forum/alia-cassandra)
hosted on google groups.
Do not hesitate to ask your questions there.

## License

Copyright © 2013 [Max Penet](https://twitter.com/mpenet)

Distributed under the Eclipse Public License, the same as Clojure.
