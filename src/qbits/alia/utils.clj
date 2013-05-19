(ns qbits.alia.utils)

(defmacro var-root-setter [x]
  `(fn [arg#]
     (alter-var-root (var ~x)
                     (constantly arg#)
                     (when (thread-bound? (var ~x))
                       (set! ~x arg#)))))
