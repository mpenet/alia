# alia
<!-- [![Build Status](https://secure.travis-ci.org/mpenet/alia.png?branch=master)](http://travis-ci.org/mpenet/alia) -->

> Coan-Teen, the female death spirit who walks without feet.

Cassandra CQL3 client for Clojure wrapping [datastax/java-driver](https://github.com/datastax/java-driver).

!! Early release, [datastax/java-driver](https://github.com/datastax/java-driver) is still not published on maven and in active developpement.

## But why yet another one?

You would think I'd get bored of cassandra clients, but
[datastax/java-driver](https://github.com/datastax/java-driver) looks
very promising.

It's built on top of the new binary protocol, can handle all the
pooling/balancing/failover for you, is very active, has synchronous and
asynchronous APIs, is likely to become the standard client for java
(it's the only one I know that uses the new protocol) and you can trust
[datastax](http://datastax.com/) people to improve and maintain it.

Thrift clients are still very relevant though, CQL3 brings a lot of
high level features, but for now it still feels incomplete on some
aspects.

If you want a Thrift based client for Clojure you could give a try to [casyn](https://github.com/mpenet/casyn)

## What Alia can do and will do soon.

It's relatively low level for now.

### Can do

* Nice simple api to work with string queries or prepared statements,
  sync/async (using regular functions, promises or callbacks depending
  on the mode you choose), with transparent handling of clojure
  datatypes (all cassandra data types are supported).

* Query generation from a cute korma'ish dsl (in developement here: [hayt](https://github.com/mpenet/hayt))

* The exposed parts of the public api all allow full access to
  java-driver API if you need to leverage some of the advanced
  functionalities it provides (that is until I provide some wrappers
  around that).

### Will do soon


* Support for more external lib data types (joda time and
  com.eaio.uuid's are already supported)
* More sugar around retry/pooling/balancing options
* Proper documentation

## Show me some code!

First some [API docs](http://mpenet.github.com/alia).

```clojure

(require '[qbits.alia :as alia] )

(def cluster (alia/cluster "localhost"))

;; Sessions are separate so that you can interact with multiple
;; keyspaces from the same cluster definition
(def session (alia/connect cluster))

(alia/execute session "CREATE KEYSPACE alia
                       WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 3};")

;; Every function that requires session as first argument can be also
;; used without this argument if you provide a binding (valid for
;; alia/execute, alia/prepare, alia/bind):
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
  (-> prepared-statement
      (alia/bind "frodo") ;; If you have more args: (alia/bind "foo" "bar" 1 (java.util.Date.)) etc...
      alia/execute)
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

  ;; or if you prefer array-maps you can use rows->maps
  (-> prepared-statement
      (alia/bind "frodo")
      alia/execute
      alia/rows->maps)

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

  ;; Asynchronous interface:

  ;; If you pass async? true then execute returns
  ;; a [promise](http://clojuredocs.org/clojure_core/clojure.core/promise) (non blocking)
  (def result (alia/execute "select * from users;" :async? true))

  ;; To get the result once it's realized we can dereference it, which
  ;; is a blocking operation
  @result

  ;; or using success/error handlers (it still returns a promise just like before)
  (alia/execute "select * from users;"
                :success (fn [r] (do-something-with-result r)
                :error (fn [e] (print "fail!")))))

;; cleanup
(shutdown session)
(shutdown cluster)

```

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
     (group-by [:bar :asc])
     (using :ttl 10000))

;; To compile the queries just use ->cql or ->prepared

(->cql (select :foo))
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

Then add this to your dependencies:

```clojure
[cc.qbits/alia "0.1.0-SNAPSHOT"]
```

## License

Copyright © 2013 Max Penet

Distributed under the Eclipse Public License, the same as Clojure.
