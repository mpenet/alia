# Changelog

### 1.0.0-beta5

* BREAKING CHANGE: Column names are now returned as keywords by
  default (way more convenient in real world use), you can change that
  globally using `set-keywordize!`or per query using the
  `:keywordize?` option on `execute`/`execute-async` calls.

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
