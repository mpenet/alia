(ns qbits.alia.error
  (:import
   [java.util.concurrent ExecutionException]
   [com.datastax.oss.driver.api.core.cql
    Statement
    SimpleStatement
    PreparedStatement
    BoundStatement
    BatchStatement]))

(defprotocol IStatementQuery
  (statement-query [stmt]))

(extend-protocol IStatementQuery
  SimpleStatement 
  (statement-query [stmt]
    [:simple (.getQuery stmt)])
  PreparedStatement
  (statement-query [stmt]
    [:prepared (.getQuery stmt)])
  BoundStatement
  (statement-query [stmt]
    [:bound (-> stmt .getPreparedStatement .getQuery)])
  BatchStatement
  (statement-query [stmts]
    [:batch 
     {:type (str (.getBatchType stmts))}
     (for [stmt stmts] (statement-query stmt))])
  Object
  (statement-query [obj]
    [:unknown (-> obj .getClass .getName)])
  nil
  (statement-query [stmt]
    [:nil]))

(defn ^:no-doc ex->ex-info
  "wrap an exception with some context information"
  ([^Exception ex 
    {stmt :statement
     :as data} 
    msg]
   (let [stmt-query (statement-query stmt)]
     (ex-info msg
              (merge {:type :qbits.alia/execute
                      :query stmt-query}
                     data)
              (if (instance? ExecutionException ex)
                (ex-cause ex)
                ex))))
  ([ex data]
   (ex->ex-info ex data "Query execution failed")))
