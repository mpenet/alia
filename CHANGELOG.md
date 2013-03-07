# Changelog

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
