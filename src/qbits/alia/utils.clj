(ns qbits.alia.utils)

(defn enum-values->map
  [enum-values]
  (reduce
   (fn [m hd]
     (assoc m (-> (.name ^Enum hd)
                  (.toLowerCase)
                  keyword)
            hd))
   {}
   enum-values))
