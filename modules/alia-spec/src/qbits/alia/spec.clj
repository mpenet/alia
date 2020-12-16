(ns qbits.alia.spec
  "Basic specs for validation/instrumentation, atm this doesn't
  include gen in most cases, not sure it ever will"
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   [qbits.alia :as alia]
   [qbits.alia.enum :as enum]
   [qbits.commons.enum :as ce]
   [qbits.alia.result-set :as result-set])
  (:import
   [java.util.concurrent Executor CompletionStage CompletableFuture]
   [com.datastax.oss.driver.api.core
    CqlSession
    DefaultConsistencyLevel]

   [com.datastax.oss.driver.api.core.loadbalancing NodeDistance]

   [com.datastax.oss.driver.api.core.servererrors DefaultWriteType]

   [com.datastax.oss.driver.api.core.retry RetryPolicy]
   [com.datastax.oss.driver.api.core.cql
    Statement
    PreparedStatement
    BatchStatement
    BoundStatement
    BoundStatementBuilder
    SimpleStatement
    DefaultBatchType]

   (java.nio ByteBuffer)
   (java.util.concurrent Executor)))

(defn ns-as
  "Creates a namespace 'n' (if non existant) and then aliases it to 'a'"
  [n a]
  (create-ns n)
  (alias a n))

(def string-or-named? #(or (string? %) (instance? clojure.lang.Named %)))

(s/def ::alia/session #(instance? CqlSession %))
(s/def ::alia/query #(satisfies? alia/PStatement %))
(s/def ::alia/prepared-statement #(instance? PreparedStatement %))
(s/def ::alia/completion-stage #(instance? CompletionStage))

;; enums
(letfn [(enum-key-set [enum] (-> enum ce/enum->map keys set))]
  (s/def ::enum/node-distance (enum-key-set NodeDistance))
  (s/def ::enum/write-type (enum-key-set DefaultWriteType))
  (s/def ::enum/consistency-level (enum-key-set DefaultConsistencyLevel))
  (s/def ::enum/batch-type (enum-key-set DefaultBatchType)))


;; execute & co
(create-ns 'qbits.alia.statement-options)
(alias 'alia.statement-options 'qbits.alia.statement-options)
(s/def ::alia.statement-options/consistency-level ::enum/consistency-level)
(s/def ::alia.statement-options/idempotent? boolean?)

(s/def ::alia.statement-options/page-size pos-int?)
(s/def ::alia.statement-options/paging-state #(instance? ByteBuffer %))

(s/def ::alia.statement-options/query-timestamp pos-int?)

(s/def ::alia.statement-options/routing-key #(instance? ByteBuffer %))

(s/def ::alia.statement-options/retry-policy #(instance? RetryPolicy %))


(s/def ::alia.statement-options/serial-consistency-level ::enum/consistency-level)

(s/def ::alia.statement-options/timeout pos-int?)
(s/def ::alia.statement-options/tracing boolean?)

(s/def ::alia/statement-options
  (s/keys :opts-un
          [::alia.statement-options/routing-key
           ::alia.statement-options/retry-policy
           ::alia.statement-options/tracing?
           ::alia.statement-options/idempotent?
           ::alia.statement-options/consistency
           ::alia.statement-options/serial-consistency
           ::alia.statement-options/fetch-size
           ::alia.statement-options/timesamp
           ::alia.statement-options/paging-state
           ::alia.statement-options/read-timeout]))

(create-ns 'qbits.alia.execute-opts)
(alias 'alia.execute-opts 'qbits.alia.execute-opts)

(s/def ::encodable (s/spec any?
                         :gen (fn [] (sg/return identity))))
(s/def ::decodable (s/spec any?
                         :gen (fn [] (sg/return identity))))

(s/def :qbits.alia.codec/encoder
  (s/spec (s/fspec :args (s/cat :value ::encodable)
                   :ret any?)
          :gen (sg/return identity)))

(s/def :qbits.alia.codec/decoder
  (s/spec (s/fspec :args (s/cat :value ::decodable)
                   :ret any?)
          :gen (fn [] (sg/return identity))))

(s/def ::alia.execute-opts/codec (s/keys :req-un [:qbits.alia.codec/encoder
                                                  :qbits.alia.codec/decoder]))

(s/def ::alia.execute-opts/values
  (s/or :named-values (s/map-of keyword? ::encodable :min-count 1)
        :positional-values (s/+ ::encodable)))

(s/def ::alia.execute-opts/result-set-fn
  (s/fspec :args (s/cat :result-set any?)
           :ret any?))

(s/def ::alia.execute-opts/row-generator #(satisfies? result-set/RowGenerator %))

(s/def ::alia/execute-opts-common
  (s/keys :opts-un
          [::alia.execute-opts/values
           ::alia.execute-opts/codec
           ::alia.execute-opts/result-set-fn
           ::alia.execute-opts/row-generator]))

(s/def ::alia/execute-opts
  (s/merge ::alia/execute-opts-common
           ::alia/statement-options))

(create-ns 'qbits.alia.execute-async)
(alias 'alia.execute-async 'qbits.alia.execute-async)
(s/def ::alia.execute-async/executor #(instance? Executor %))

;; TODO fix when we can avoid to run gen on instrument
(comment
  (s/def ::alia.execute-async/success
    (s/fspec :args (s/cat :result-set any?)
             :ret any?))

  (s/def ::alia.execute-async/error
    (s/fspec :args (s/cat :err #(instance? clojure.lang.ExceptionInfo %))
             :ret any?)))

(s/def ::alia.execute-async/success fn?)
(s/def ::alia.execute-async/error fn?)
(s/def ::alia.execute-async/channel any?)

(s/def ::alia/execute-async-opts
  (s/merge ::alia/execute-opts
           (s/keys :opt-un [::alia.execute-async/success
                            ::alia.execute-async/error
                            ::alia.execute-async/executor
                            ::alia.execute-async/channel])))

;; functions

(s/fdef qbits.alia/bind
        :args (s/cat :statement ::alia/prepared-statement
                     :values (s/nilable ::alia.execute-opts/values)
                     :codec (s/? ::alia.execute-opts/codec))
        :ret #(instance? BoundStatement %))

(s/fdef qbits.alia/prepare
  :args (s/cat :session ::alia/session
               :query ::alia/query)
  :ret ::alia/prepared-statement)

(s/fdef qbits.alia/prepare-async
  :args (s/cat :session ::alia/session
               :query ::alia/query)
  :ret ::alia/completion-stage)

(s/fdef qbits.alia/batch
        :args (s/cat :statements (s/spec (s/+ ::alia/query))
                     :type (s/? ::enum/batch-type)
                     :codec (s/? ::alia.execute-opts/codec))
        :ret #(instance? BatchStatement %))

(s/fdef qbits.alia/execute
        :args (s/cat :session ::alia/session
                     :query ::alia/query
                     :options (s/? ::alia/execute-opts))
        :ret ::alia/completion-stage)

(s/fdef qbits.alia/execute-async
        :args (s/cat :session ::alia/session
                     :query ::alia/query
                     :options (s/? ::alia/execute-async-opts))
        :ret #(instance? CompletableFuture %))

(s/fdef qbits.alia/udt-encoder
        :args (s/cat :session ::alia/session
                     :keyspace (s/? string-or-named?)
                     :udt-type string-or-named?))

(s/fdef qbits.alia/tuple-encoder
        :args (s/cat :session ::alia/session
                     :keyspace (s/? string-or-named?)
                     :table string-or-named?
                     :column string-or-named?))
