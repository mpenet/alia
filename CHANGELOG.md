# Changelog

### X.X.X

* Moved hayt query strategies in qbits.alia.hayt:
  `set-hayt-raw-fn!` becomes `qbits.alia.hayt/set-query-strategy!`
  ex:
  ```clojure
  (qbits.alia.hayt/set-query-strategy! (qbits.alia.hayt/query-strategy :lu 100))
  ```
  A query strategy is just a function of 1 arg that transforms an
  input hayt query to a string query, a number of strategies are available from
  `qbits.alia.hayt/query-strategy` are `:lu` `:lru` `:ttl` `:fifo` and
  `:raw`, they map to the corresponding function in core.memoize.

  You can to set qbits.alia.hayt/*query-fn* directly with
  your own strategy.

### 1.0.0-rc1

* Upgraded to java-driver rc1

* Upgraded hayt to 0.5.0

* Restored `keywordize?`

### 1.0.0-beta9

* Lift restriction on nil values (supported in C* 1.2.4+)

### 1.0.0-beta8

* remove `keywordize` option, useless feature

### 1.0.0-beta7

* Update java-driver to beta2, see
  [driver-core/CHANGELOG](https://github.com/datastax/java-driver/blob/master/driver-core/CHANGELOG.rst)

* Metadata on results updated to follow java-driver's style, it now returns an
  `:execution-info` key that contains basic info about the query
  (hosts queried etc) and allows to retrieve tracing info, in future
  versions paging metadata probably, see
  [JAVA-65](https://datastax-oss.atlassian.net/browse/JAVA-65).

* C* CUSTOM type support see
  [JAVA-61](https://datastax-oss.atlassian.net/browse/JAVA-61)

### 1.0.0-beta6

* `async-execute` now returns a `lamina.core/result-channel`, the only
  difference for end-users should be the behavior when an error occurs
  and the query is dereferenced: it used to return the exception as a
  value, now it throws it, callbacks are unchanged.

* Added [Lamina](https://github.com/ztellman/lamina) as a dependency.

### 1.0.0-beta5

* BREAKING CHANGE: Column names are now returned as keywords by
  default (way more convenient in real world use), you can change that
  globally using `set-keywordize!`or per query using the
  `:keywordize?` option on `execute`/`execute-async` calls.

* Depend on clojure 1.5.1 by default.

### 1.0.0-beta4

* update hayt to 0.4.0-beta3
* update core.memoize to 0.5.3
* minor code changes to avoid repetitions in alia.clj

### 1.0.0-beta3

* add hayt raw query execution support, with query caching.

### 1.0.0-beta2

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
