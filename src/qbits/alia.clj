(ns qbits.alia
  (:require [qbits.knit :as knit]
            [qbits.alia.codec :as codec])
  (:import [com.datastax.driver.core
            Cluster
            Session
            DataType
            DataType$Name]))

;; (prn DataType/set)

(declare result-set->clojure)

(defmulti set-builder-option (fn [k builder option] k))

(defmethod set-builder-option :contact-points
  [_ builder hosts]
  (.addContactPoints builder (into-array (if (sequential? hosts) hosts [hosts]))))

(defmethod set-builder-option :port
  [_ builder port]
  (.withPort builder (int port)))

(defmethod set-builder-option :load-balancing-policy
  [_ builder policy]
  builder)

(defmethod set-builder-option :reconnecting-policy
  [_ builder policy]
  builder)

(defmethod set-builder-option :load-balancing-policy
  [_ builder policy]
  builder)

(defmethod set-builder-option :pooling-options
  [_ builder options]
  builder)

(defmethod set-builder-option :socket-options
  [_ builder options]
  builder)

(defmethod set-builder-option :metrics?
  [_ builder metrics?]
  (when (not metrics?)
    (.withoutMetrics builder)))

(defmethod set-builder-option :auth-info
  [_ builder options]
  builder)

(defn set-builder-options
  [builder options]
  (reduce (fn [builder [k option]]
            (prn (format "Setting %s %s" k option))
            (set-builder-option k builder option))
          builder
          options))

(defn cluster
  "Returns a new cluster instance"
  [hosts & {:as options}]
  (-> (Cluster/builder)
      (set-builder-options (assoc options :contact-points hosts))
      .build))

(defn ^Session connect
  "only 1 ks per session, so this needs to be separate"
  ([^Cluster cluster keyspace]
     (.connect cluster keyspace))
  ([^Cluster cluster]
     (.connect cluster)))

(defn shutdown
  [cluster-or-session]
  (.shutdown cluster-or-session))

(defn prepare [session query]
  (.prepare session query))

(def ^:dynamic *default-async-executor* (knit/executor :cached))

(defn execute
  [^Cluster cluster query & {:keys [callback async-executor]
                             :or [async-executor *default-async-executor*]}]
  (if callback
    (let [async-result (promise)
          rs-future (.executeAsync cluster query)]
      (.addListener rs-future
                    (fn []
                      (let [result (result-set->clojure (.get rs-future))]
                        (deliver async-result result)
                        (callback result)))))
    (-> (.execute cluster query)
        result-set->clojure)))

(defn result-set->clojure
  [result-set]
  (map (fn [row]
         (let [cdef (.getColumnDefinitions row)]
           (map-indexed
            (fn [idx col]
              {:name (.getName cdef idx)
               :value (codec/decode row (int idx) (.getType cdef idx))})
            cdef)))
       result-set))
