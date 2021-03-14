(ns com.verybigthings.pgerrors.test-helpers
  (:require [clojure.test :refer :all]
            [next.jdbc :as jdbc]
            [clojure.java.io :as io]
            [next.jdbc.result-set :as rs]))

(def db-uri "jdbc:postgresql://localhost:5432/pgerrors_dev?user=postgres")

(defn reset-db []
  (let [sql     (-> (str "com/verybigthings/pgerrors/test_db_scripts/migration.sql") io/resource slurp)
        schemas (jdbc/execute! db-uri ["select schema_name from information_schema.schemata where catalog_name = 'pgerrors_dev' and schema_name not like 'pg_%' and schema_name not like 'information_schema'"] {:builder-fn rs/as-unqualified-lower-maps})]
    (doseq [s schemas]
      (jdbc/execute! db-uri [(str "DROP SCHEMA " (:schema_name s) " CASCADE")]))
    (jdbc/execute! db-uri [sql])))

(defn reset-db-fixture [f]
  (reset-db)
  (f))