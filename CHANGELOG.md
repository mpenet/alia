# Changelog

## 3.1.5

* Use java-driver 3.0.1 [driver-core/CHANGELOG](http://datastax.github.io/java-driver/changelog/#3-0-1)

## 3.1.4

* SSLOptions in `alia` main module: https://github.com/mpenet/alia/pull/55
  @blak3mill3r fixed ssl options handling in cluster builder

## 3.1.3

* kill stray qbits.alia file in qbits.alia.async causing major breakage

## 3.1.2

* remove aliases to async functions since it acutally doesn't work
  (you can import them from `qbits.alia.async`).

## 3.1.0

**Breaking changes (package/dependency level, not code)**

* Alia was split into separate libaries to allow more flexible builds,
  lighter dependencies, better aot'isability and also make it ok to
  add more exotic features without adding weight on the core (an
  upcoming Component, optional schemas for cluster options, direct
  linking, etc).

  If you don't really care and want the whole package like before, you
  can just change your dependencies to `[cc.qbits/alia-all "3.1.0"]`.
  If you were using `execute-chan` or `execute-chan-buffered`, you
  will now find these 2 function in `qbits.alia.async`

  Alia was split like this:

    + `alia`: minimal core of the driver (whithout core.async/manifold/custom codec extensions)
    + `alia-async`: core.async interface (`qbits.alia/execute-chan`, `qbits.alia/execute-chan-buffered`)
    + `alia-manifold`: manifold async interaface (`qbits.alia.manifold/*` same as before)
    + `alia-eaio-uuid`: eaio.uuid codec extension
    + `alia-joda-time`: joda-time codec extension
    + `alia-nippy`: the Nippy codec extension

  They are located in [./modules](https://github.com/mpenet/alia/tree/master/libs).
  The version numbers will always match the core lib, `alia` to avoid confusions.

  A simple example, if you only want to use the core and the
  core.async extenstion you can just add these to your dependencies:

    `[cc.qbits/alia "3.1.2"]
     [cc.qbits/alia-async "3.1.2"]`

## 3.0.0

* Use java-driver 3.0.0 (final) [driver-core/CHANGELOG](https://github.com/datastax/java-driver/tree/3.0/changelog)

* Simple statements can now take named placeholders (same as prepared):

```clojure
(execute session "SELECT * FROM foo WHERE bar = :bar-value" {:values {:bar-value "something"}})`
```

* Add per query `read-timeout` via execute options

## 3.0.0-rc2

**Breaking change**

* `Hayt` is not included by default anymore. You can include it
  manually in your dependencies, then simply loading `qbits.hayt` in
  the namespace(s) where you use it with alia and will extend the
  appropriate protocol and **provide total backward compatibility**.  (I
  had to do this to accomodate users which had dependency conflicts
  with hayt (and were not using it)).

  tldr: add `[cc.qbits/hayt "3.0.0"]` in your deps
  if/when you use it.

## 3.0.0-rc1

* Use java-driver 3.0.0-rc1 [driver-core/CHANGELOG](https://github.com/datastax/java-driver/tree/3.0/changelog)

## 3.0.0-beta1

* typo translater -> translator (from upstream)

* remove hayt query caching, let this to the end user to manage if wanted

More infos here about using 3.x: https://github.com/datastax/java-driver/tree/3.0/upgrade_guide

## 2.12.1

* Correct cassandra-driver-dse exclusions see https://github.com/mpenet/alia/pull/47

## 2.12.0

* Use java-driver 2.1.9 [driver-core/CHANGELOG](https://github.com/datastax/java-driver/tree/2.1/changelog)

## 2.11.0

* Use latest core.async: as a result `qbits.alia/execute-chan` now
  returns a core.async/promise-chan.

* Update core.memoize dependency

## 2.10.0

* Use java-driver 2.1.8 [driver-core/CHANGELOG](https://github.com/datastax/java-driver/tree/2.1/changelog)

## 2.9.0

* Respect nullability of row cells: previously a Boolean column would
  be False when no value was set, a Long would always default to 0 and
  so on. This differed from cqlsh and what's general expected and was
  a side effect of the java API available at the time the decoding
  code was written.

* Handle custom decoding in deeply nested datastructures via PCodec
  protocol https://github.com/mpenet/alia/issues/45. It's on by default
  but it is possible to turn this off by extending PCodec protocol for
  Map, List, Set, TupleValue, UDTValue to just be "identity".

## 2.8.0 - clojure 1.7 required

* ResultSet decoding is now done via a protocol that implements both
seq() and IReduceInit(), the former would return an unchunked lazy seq
(it's the detault, same as previous versions, it's equivalent to
passing `{:result-set-fn seq}` to execute), and the later would you to
get a reducible for instance if you pass `#(into [] %)` as a
`:result-set-fn`, which is eager and cheaper. `IReduceInit` is 1.7+
only, hence the new requirement.

* Via `:result-set-fn` you can also reach ExecutionInfos from the rs
(which was previously always set as metadata to rs) by calling
qbits.codec/execution-info on the argument passed to your function,
from there you could for instance log the information returned.

* Breaking: `:string-keys?` is removed in favor of `:key-fn`

## 2.7.4

* drop :exception-info metadata on result-set

* prevent calls to ResultSet.one() when not needed (true fire and forget)

## 2.7.3

* manifold interface, fix NPE when missing success or error handler
  passed to manifold/on-realized

## 2.7.2

* All enum taking fns will now reject invalid values and throw if
something weird is supplied

* row decoding is now truly lazy, skipping clojure chunking and
  respecting fetch-size more accurately (especially important for
  execute-chan-buffered).

* invalid udt/tuple encoders now throw if supplied an invalid type name

## 2.7.1

**Breaking changes**

* UDT field names are now decoded as clojure Keywords instead of
  strings

** New Features **

* `qbits.alia/tuple-encoder` and `qbits.alia/udt-encoder`: add Tuple
  and UDT encoder functions to be used with prepared statements. They
  both return a function that can be used to encode tuple/udt of the
  selected type.  ex:

  ```clojure
  (let [user (qbits.alia/udt-encoder session :user)]
    (execute session
             user-insert-prepared-stmt
             {:values [(user {:id "foo" :age 10})]}))
  ```

  Internal encoding of values respects the main encoder
  (`qbits.codec.PCodec`), that means if you extended it for joda time
  or your own types for this should work transparently even
  if your UDT are deeply nested.

## 2.6.2

* fix decoding of tuple/udt values when NULL (issue #40)

## 2.6.1

* Meh, Broken deploy

## 2.6.0

* Add support to named placeholders in prepared statements (:value can
take a map that will either match placeholders of column name
values) - Thanks to @d-t-w

* Add `qbits.alia/batch `

* Use shadded Netty dependency (allows to use another netty version in
  your code if needed) - Thanks to @d-t-w

* Remove deprecated Lamina interface

* Improve decoding performance

* More uniform exception handling in async modes (all exceptions are
returned in chan/deferred/function depending on the context)

## 2.5.3

* Fix possible chan leaks/flow break in async error handling (thanks to @d-t-w).
See issue #29 for details.

## 2.5.2

* fix some bugs in the nippy encoder

## 2.5.1

* Add simple callback based alia/execute-async (takes :success and/or
  :error functions via option map)

## 2.4.0

* Use java-driver 2.1.6
[driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.1/driver-core/CHANGELOG.rst)

* adds cluster options and related utils for: speculative execution,
  address-translater, cluster-name, netty-options,
  timestamp-generator, max-schema-agreement-wait-seconds

* new execute* options for `:paging-state` and `idempotent?`

## 2.3.9

* Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md

## 2.3.8

* Improve joda-time codec for java.util.Date decoding from data
returned by cassandra

* Adds experimental nippy codec with various modes:
+ by calling (set-nippy-collection-encoder! nippy-opts) and
  (set-nippy-collection-decoder! nippy-opts) all collections passed to
  alia will be stored as ByteBuffer and decoded back to clj
  collections when queried. This is fast, but breaks cassandra native
  collections encoding in prepared statements. If you need to handle
  both the other mode is for you.
+ the other mode requires you to call
  qbits.alia.codec.nippy/serializable! on the data you want to have
  serialized. All bytebuffers returned by cassandra will be considered as
  nippy data.

If you need more fine grained control you are be better off calling
thaw/freeze manually on a per column basis in your app.

## 2.3.7

* Add decode function to PCodec protocol to allow custom decoding (ex joda)

## 2.3.6

* allow to specifiy timestamp via query options

## 2.3.5

* fix encoding error in joda-time codec

## 2.3.4

* remove log4j properties file used for debugging locally that sneaked in!

## 2.3.3

* Use java-driver 2.1.5
[driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.1/driver-core/CHANGELOG.rst)

## 2.3.2

* improve resource use and performance in async mode by running the
  put! in the io thread (since it's async anyway), not using an
  external thread pool. You can still pass :executor to the execute-*
  call and override this behavior.

* remove now useless qbits.knit dependency


## 2.3.1

* bump hayt dependency (c* 2.1+ udt support)
* add optional [manifold](https://github.com/ztellman/manifold)
  interface, see `qbits.alia/manifold`

## 2.3.0

* add support for Kerberos (via `:kerberos?` option) & TLS by adding `:ssl-options` map argument
  support, it now accepts an SSLOption instance (as before) or a map
  of `:keystore-path` `:keystore-password` `:cipher-suites`.

## 2.3.0-rc2

* Use java-driver 2.1.4
[driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.1/driver-core/CHANGELOG.rst)

## 2.3.0-rc1

* Use java-driver 2.1.3
[driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.1/driver-core/CHANGELOG.rst)

## 2.2.3

* Use java-driver 2.1.2 [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.1/driver-core/CHANGELOG.rst)

## 2.2.2

* Use java-driver 2.1.1 [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.1/driver-core/CHANGELOG.rst)

## 2.2.1

* Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md


## 2.2.0

* Lamina async interface is deprecated and moved in a separate ns. If
  you sill use it, you'll need to bring lamina as a dependency to your project.

## 2.1.2

* Allow execution of parameterized statements with their values in 1 roundtrip.

```clojure
(execute session "select * from foo where bar = ?;" {:values ["baz"]})
(execute session (select :foo (where [[= :bar ?]])) {:values ["baz"]})
...
```

## 2.1.1

* Full support for CQL UDT & TUPLE: Auto-decoding of CQL Tuple and
  User Data Types. You can insert them using literals in both prepared
  statements and raw queries.  See article about this
  [here](http://www.datastax.com/dev/blog/datastax-java-driver-2-1)
  and [there](http://www.datastax.com/dev/blog/cql-in-2-1).

## 2.1.0

* Use java-driver 2.1.0
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.1/driver-core/CHANGELOG.rst)

## 2.1.0-rc3

* Update to latest core.async

## 2.1.0-rc2

* Empty List now returns [] instead of nil, to match Map and Set types
* Update to latest Lamina (manifold support), core.async, core.cache, clj-time

## 2.1.0-rc1

* Use java-driver 2.1.0-rc1
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.1/driver-core/CHANGELOG.rst)

## 2.0.0-rc4

* Use java-driver 2.0.3
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.0/driver-core/CHANGELOG.rst)

* Use latest core.async

## 2.0.0-rc3

* Add `execute-chan-buffered`:
Allows to execute a query and have rows
returned in a `clojure.core.async/chan`. Every value in the chan is
a single row. By default the query `:fetch-size` inherits from the
cluster setting, unless you specify a different `:fetch-size` at
query level and the channel is a regular `clojure.core.async/chan`,
unless you pass your own `:channel` with its own sizing caracteristics.
`:fetch-size` dicts the "chunking" of the rows returned, allowing to
"stream" rows into the channel in a controlled manner.

The name of this fn could be subject to change in future versions.

## 2.0.0-rc2

* Use java-driver 2.0.2
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.0/driver-core/CHANGELOG.rst)

## 2.0.0-rc1

**Breaking changes** -> API cleanup, performance, better composability

* We got rid of all the dynamic vars and related functions/macros
  (`set-*`, `with-*`)
* All `execute*` functions, as well as `prepare`, now require an
  explicit session argument (it was optional before), options are now
  a map instead of kwargs
* `cluster` now takes a map instead of hosts + kwargs
* Performance improvement (as a result of the previous changes)
* No longer leaks the default-executor for async, it will be
  initialized lazily the first time it's needed (if at all)

The next release will likely be 2.0 final, and the API will be stable
from now on.

## 2.0.0-beta11

* **Breaking changes** Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md

## 2.0.0-beta10

* Use java-driver 2.0.1
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.0/driver-core/CHANGELOG.rst)

## 2.0.0-beta9

* Use java-driver 2.0.0 final
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.0/driver-core/CHANGELOG.rst)

## 2.0.0-beta8

* Use java-driver 2.0.0-rc3
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.0/driver-core/CHANGELOG.rst)

## 2.0.0-beta7

* Use Lamina 0.5.2, fixed repl reloading see https://github.com/ztellman/lamina/issues/82

## 2.0.0-beta6

* Add QueryOptions [com.datastax.driver.core.QueryOptions](http://www.datastax.com/drivers/java/2.0/apidocs/com/datastax/driver/core/QueryOptions.html)
  support at cluster level

## 2.0.0-beta5

* Add [Statement.setFetchSize](http://www.datastax.com/drivers/java/2.0/com/datastax/driver/core/Statement.html#setFetchSize(int)) - Thanks @coltnz

* Add serial consistency support

## 2.0.0-beta4

* Use newest core.async

## 2.0.0-beta3

* Use java-driver 2.0.0-rc2
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.0/driver-core/CHANGELOG.rst)
* Use latest core.async

## 2.0.0-beta2

* Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md

## 2.0.0-beta1

### **Breaking changes** (if you are using Cassandra 2.0- you need to keep using 1.10.2)

* Use java-driver 2.0.0-rc1
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/2.0/driver-core/CHANGELOG.rst)

* keyword cql-values are no longer encoded as strings (you have to
  manually handle this), since keywords will be used as named bind
  markers in the future.

* Breaking changes: Use latest Hayt (2.x) https://github.com/mpenet/hayt/blob/master/CHANGELOG.md

## 1.10.2

* Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md

## 1.10.1

* Fix/modify ExceptionInfo map, we don't try to get queryString
  manually since it's not always available (BoundStatement vs
  Statement) and generates an error.

## 1.10.0 **Breaking change**

* Improved exception handling, query
  execution/binding/preparation exception are now ExceptionInfo
  instances that hold a map with the original statement and the Query
  string used.  You can get to this info from the ExceptionInfo
  instance using `clojure.core/ex-data`.

  ```clj
  (try
    (execute "slect prout from 1;")
    (catch Exception ex
       (println (ex-data ex))))
  ```
  The map looks like this:
  ```clj
  {:exception #<SyntaxError com.datastax.driver.core.exceptions.SyntaxError: line 1:0 no viable alternative at input 'slect'>
   :query "slect prout from 1;"
   :values nil
   :statement #<SimpleStatement slect prout from 1;>}
  ```

## 1.9.2

* Use java-driver 1.0.4
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/1.0/driver-core/CHANGELOG.rst)
* Add add support for `:socket-options` and `:defer-initialization` via
  `cluster` options
* Use new java-driver 1.0.4 api for pooling-options & socket-options
  (backward compatible)

## 1.9.1

* Update clojure.core.async dependency and cleanup project.clj

## 1.9.0

### **Breaking change** `qbits.alia/prepare`

* `prepare` used to be able to convert the query argument when coming
from hayt using `->prepared` and only take the first value of the
returned vector. While this was useful, this was often hiding the
resulting query and limiting in some way (you don't necessarly want to
have every value prepared), forcing the user to compile it in the repl
first to have an idea of what it would look like.  `prepare` has been
changed so that it never compiles with `->prepare` but now does it
with `->raw`.
Meaning you can now do the following `(prepare (select :foo (where {:bar ?})))`
You can still use `prepare` with queries generated with `->prepared`
you just need to do it explicitly
```clojure
(def pq (->prepared (select :foo (where {:bar 1}))))
(prepare (first pq))
...
```

## 1.8.3

* Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md

## 1.8.2

* Allow to pass keyspace id as a keyword in `qbits.alia/connect`

## 1.8.1

* Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md

## 1.8.0

* Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md
* Use  clojure.core.async alpha release instead of SNAPSHOT

## 1.8.0-beta3

* Use java-driver 1.0.3 [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/master/driver-core/CHANGELOG.rst)

## 1.8.0-beta2

* Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md

* Make sure namespace keywords are properly encoded in prepared statements

## 1.8.0-beta1

* Add support for clojure.core.async via `qbits.alia/execute-chan`,
  returning a channel usable with go blocks or `clojure.core.async/take!`.

## 1.7.1

* Use latest Hayt https://github.com/mpenet/hayt/blob/master/CHANGELOG.md

## 1.7.0

* Use java-driver 1.0.2 [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/master/driver-core/CHANGELOG.rst)

## 1.6.0

* Use java-driver 1.0.1 [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/master/driver-core/CHANGELOG.rst)

* Add SSL support

## 1.5.3

* Update core.memoize dependency (fixes annoying assert bug)

## 1.5.2

* Make it clj 1.4+ compatible instead of 1.5+ (dependency on latest hayt)

## 1.5.1

* Add support for raw byte-arrays from hayt queries and prepared statements

## 1.5.0

* Add support for hayt query input in `alia/prepare`

## 1.4.1

* bump hayt to 1.0.5 https://github.com/mpenet/hayt/blob/master/CHANGELOG.md#105

* bump core.memoize to 0.5.5

## 1.4.0

* add simple wrapper to time 1 UUIDs

## 1.3.2

* update dependency `core.memoize` to 0.5.4

## 1.3.1

* ResultSet decoding performance improvements

## 1.3.0

* Added support for `JMXReporting` toggling at the cluster level

## 1.2.0

* `qbits.tardis` is now optional

* Added wrappers for retry/load-balancing/reconnection policies see `qbits.alia.policy.*`

## 1.1.0

* Added `qbits.alia/lazy-query`: lazy sequences over CQL queries

* Depend on hayt 1.0.2 (bugfixes)

## 1.0.0

* `:auth-info` option on `cluster` becomes `:credentials`
  as SimpleAuthInfoProvider is gone and replaced with .withCredentials.

## 1.0.0-rc1

* Upgraded to java-driver rc1

* Upgraded hayt to 0.5.0

* Restored `keywordize?`

## 1.0.0-beta9

* Lift restriction on nil values (supported in C* 1.2.4+)

## 1.0.0-beta8

* remove `keywordize` option, useless feature

## 1.0.0-beta7

* Update java-driver to beta2, see
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/master/driver-core/CHANGELOG.rst)

* Metadata on results updated to follow java-driver's style, it now returns an
  `:execution-info` key that contains basic info about the query
  (hosts queried etc) and allows to retrieve tracing info, in future
  versions paging metadata probably, see
  [JAVA-65](https://datastax-oss.atlassian.net/browse/JAVA-65).

* C* CUSTOM type support see
  [JAVA-61](https://datastax-oss.atlassian.net/browse/JAVA-61)

## 1.0.0-beta6

* `async-execute` now returns a `lamina.core/result-channel`, the only
  difference for end-users should be the behavior when an error occurs
  and the query is dereferenced: it used to return the exception as a
  value, now it throws it, callbacks are unchanged.

* Added [Lamina](https://github.com/ztellman/lamina) as a dependency.

## 1.0.0-beta5

* BREAKING CHANGE: Column names are now returned as keywords by
  default (way more convenient in real world use), you can change that
  globally using `set-keywordize!`or per query using the
  `:keywordize?` option on `execute`/`execute-async` calls.

* Depend on clojure 1.5.1 by default.

## 1.0.0-beta4

* update hayt to 0.4.0-beta3
* update core.memoize to 0.5.3
* minor code changes to avoid repetitions in alia.clj

## 1.0.0-beta3

* add hayt raw query execution support, with query caching.

## 1.0.0-beta2

* update hayt to 0.4.0-beta1

## 0.3.1

* add `keywordize?` options to `execute` and `execute-async`, make it
  settable globally with `set-keywordize!`.

## 0.3.0

* Breaking change!
  Add `execute-async`: use distinct functions for sync/async mode
  since they don't share return types and some optional
  parameters. This means `execute` no longer accepts the `:async?`
  option, use `execute-async` instead.

## 0.2.0

* fixed pooling options only allowing one distance setting, and also
  make it use hash-map as value

  ```clojure
   :core-connections-per-host {:remote 10 :local 100}
  ```
