# Changelog

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
