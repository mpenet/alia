(ns qbits.alia.query-logger
  "Experimental, subject to changes"
  ;; (:import
  ;;  (com.datastax.driver.core
  ;;   PerHostPercentileTracker
  ;;   QueryLogger
  ;;   QueryLogger$Builder))
  )

;; TODO remove this namespace? QueryLogger seems like it no longer exists in java-driver-4

;; (defmulti set-query-logger-option! (fn [k ^QueryLogger$Builder b & _] k))

;; (defmethod set-query-logger-option! :constant-threshold
;;   [_ ^QueryLogger$Builder b slow-query-latency-threshold-ms]
;;   (.withConstantThreshold b (long slow-query-latency-threshold-ms)))

;; (defmethod set-query-logger-option! :dynamic-threshold
;;   [_ ^QueryLogger$Builder b
;;    per-host-percentile-latency-tracker
;;    slow-query-latency-threshold-percentile]
;;   (.withDynamicThreshold b
;;                          per-host-percentile-latency-tracker
;;                          (double slow-query-latency-threshold-percentile)))


;; (defmethod set-query-logger-option! :max-logged-parameters
;;   [_ ^QueryLogger$Builder b max-logged-parameters]
;;   (.withMaxLoggedParameters b (int max-logged-parameters)))

;; (defmethod set-query-logger-option! :max-parameter-value-length
;;   [_ ^QueryLogger$Builder b max-parameter-value-length]
;;   (.withMaxParameterValueLength b (int max-parameter-value-length)))

;; (defmethod set-query-logger-option! :max-query-string-length
;;   [_ ^QueryLogger$Builder b max-query-string-length]
;;   (.withMaxQueryStringLength b (int max-query-string-length)))

;; (defmethod set-query-logger-option! :default
;;   [_ ^QueryLogger$Builder b option]
;;   b)

;; (defn set-query-logger-options!
;;   ^QueryLogger$Builder
;;   [^QueryLogger$Builder builder options]
;;   (reduce (fn [builder [k option]]
;;             (set-query-logger-option! k builder option))
;;           builder
;;           options))

;; (defn query-logger
;;   [cluster options]
;;   (-> (QueryLogger/builder)
;;       (set-query-logger-options! options)
;;       .build))
