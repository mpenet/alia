(ns qbits.alia.utils
  (:require [clojure.string :as string]))

(defn enum-values->map
  [enum-values]
  (reduce
   (fn [m hd]
     (assoc m (-> (.name ^Enum hd)
                  (.toLowerCase)
                  (string/replace "_" "-")
                  keyword)
            hd))
   {}
   enum-values))

(defmacro dyn-setter [name doc-string x]
  `(defn ~name
    ~doc-string
    [arg#]
    (alter-var-root (var ~x)
                    (constantly arg#)
                    (when (thread-bound? (var ~x))
                      (set! ~x arg#)))))
