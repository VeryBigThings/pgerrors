(ns com.verybigthings.pgerrors.core-test
  (:require [clojure.test :refer :all]
            [com.verybigthings.pgerrors.test-helpers :as th]
            [next.jdbc :as jdbc]
            [com.verybigthings.pgerrors.core :refer [extract-data]])
  (:import (org.postgresql.util PSQLException)))

;; Inspired by https://github.com/Shyp/go-dberror/blob/master/error_test.go

(use-fixtures :once th/reset-db-fixture)

(def uuid-1 (java.util.UUID/fromString "3c7d2b4a-3fc8-4782-a518-4ce9efef51e7"))
(def uuid-2 (java.util.UUID/fromString "91f47e99-d616-4d8c-9c02-cbd13bceac60"))
(def email-1 "test@example.com")
(def email-2 "test2@example.com")

(deftest not-null-violation
  (testing "one column"
    (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
      (try
        (jdbc/execute! t ["INSERT INTO accounts (id) VALUES(?)" nil])
        (is false)
        (catch PSQLException e
          (is (= {:postgresql.error/message "Null value in column \"id\" violates not-null constraint"
                  :postgresql.error/detail "Failing row contains (null, null, null, active, null)."
                  :postgresql/sql-state "23502"
                  :postgresql/error :not-null-violation,
                  :postgresql/columns ["id"]}
                (extract-data e)))))))
  (testing "two columns"
    (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
      (try
        (jdbc/execute! t ["INSERT INTO two_not_null (str_1, str_2) VALUES(?, ?)" nil nil])
        (is false)
        (catch PSQLException e
          (is (= {:postgresql.error/message "Null value in column \"str_1\" violates not-null constraint"
                  :postgresql.error/detail "Failing row contains (null, null)."
                  :postgresql/sql-state "23502"
                  :postgresql/error :not-null-violation,
                  :postgresql/columns ["str_1"]}
                (extract-data e))))))))

(deftest default-constraint
  (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
    (try
      (jdbc/execute! t ["INSERT INTO accounts (id, email, balance) VALUES(?, ?, -1)" uuid-1 email-1])
      (is false)
      (catch PSQLException e
        (is (= {:postgresql.error/message "New row for relation \"accounts\" violates check constraint \"accounts_balance_check\""
                :postgresql.error/detail "Failing row contains (3c7d2b4a-3fc8-4782-a518-4ce9efef51e7, test@example.com, -1, active, null)."
                :postgresql/sql-state "23514"
                :postgresql/error :check-violation
                :postgresql/constraint "accounts_balance_check"
                :postgresql/relation "accounts"}
              (extract-data e)))))))

(deftest invalid-uuid
  (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
    (try
      (jdbc/execute! t ["INSERT INTO accounts (id) VALUES('foo')"])
      (is false)
      (catch PSQLException e
        (is (= {:postgresql.error/message "Invalid input syntax for type uuid: \"foo\""
                :postgresql/sql-state "22P02"
                :postgresql/type "uuid"
                :postgresql/value "foo"
                :postgresql/error :invalid-text-representation}
              (extract-data e)))))))

(deftest invalid-json
  (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
    (try
      (jdbc/execute! t ["INSERT INTO accounts (data) VALUES('')"])
      (is false)
      (catch PSQLException e
        (is (= {:postgresql.error/message "Invalid input syntax for type json"
                :postgresql.error/detail "The input string ended unexpectedly."
                :postgresql/sql-state "22P02"
                :postgresql/type "json"
                :postgresql/error :invalid-text-representation}
              (extract-data e)))))))

(deftest invalid-enum
  (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
    (try
      (jdbc/execute! t ["INSERT INTO accounts (id, email, balance, status) VALUES(?, ?, 1, 'blah')" uuid-1 email-1])
      (is false)
      (catch PSQLException e
        (is (= {:postgresql.error/message "Invalid input value for enum account_status: \"blah\""
                :postgresql/sql-state "22P02"
                :postgresql/type "enum"
                :postgresql.type/enum "account_status"
                :postgresql/value "blah"
                :postgresql/error :invalid-text-representation}
              (extract-data e)))))))

(deftest too-large-int
  (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
    (try
      (jdbc/execute! t ["INSERT INTO accounts (id, email, balance) VALUES(?, ?, 40000)" uuid-1 email-1])
      (is false)
      (catch PSQLException e
        (is (= {:postgresql.error/message "Smallint out of range"
                :postgresql/sql-state "22003"
                :postgresql/error :numeric-value-out-of-range}
              (extract-data e)))))))

(deftest unique-constraint
  (testing "one column"
    (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
      (try
        (jdbc/execute! t ["INSERT INTO accounts (id, email, balance) VALUES(?, ?, 1)" uuid-1 email-1])
        (jdbc/execute! t ["INSERT INTO accounts (id, email, balance) VALUES(?, ?, 1)" uuid-1 email-1])
        (is false)
        (catch PSQLException e
          (is (= {:postgresql.error/message "Duplicate key value violates unique constraint \"accounts_pkey\""
                  :postgresql.error/detail "Key (id)=(3c7d2b4a-3fc8-4782-a518-4ce9efef51e7) already exists."
                  :postgresql/sql-state "23505"
                  :postgresql/columns ["id"]
                  :postgresql/constraint "accounts_pkey"
                  :postgresql/error :unique-violation}
                (extract-data e)))))))
  (testing "two columns"
    (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
      (try
        (jdbc/execute! t ["INSERT INTO two_col_uniqueness (str_1, str_2) VALUES(?, ?)" uuid-1 email-1])
        (jdbc/execute! t ["INSERT INTO two_col_uniqueness (str_1, str_2) VALUES(?, ?)" uuid-1 email-1])
        (is false)
        (catch PSQLException e
          (is (= {:postgresql.error/message "Duplicate key value violates unique constraint \"two_col_uniqueness_str_1_str_2\""
                  :postgresql.error/detail "Key (str_1, str_2)=(3c7d2b4a-3fc8-4782-a518-4ce9efef51e7, test@example.com) already exists."
                  :postgresql/sql-state "23505"
                  :postgresql/columns ["str_1" "str_2"]
                  :postgresql/constraint "two_col_uniqueness_str_1_str_2"
                  :postgresql/error :unique-violation}
                (extract-data e))))))))

(deftest unique-failure-on-update
  (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
    (try
      (jdbc/execute! t ["INSERT INTO accounts (id, email, balance) VALUES(?, ?, 1)" uuid-1 email-1])
      (jdbc/execute! t ["INSERT INTO accounts (id, email, balance) VALUES(?, ?, 1)" uuid-2 email-2])
      (jdbc/execute! t ["UPDATE accounts SET email = ? WHERE id = ?" email-1 uuid-2])
      (is false)
      (catch PSQLException e
        (is (= {:postgresql.error/message "Duplicate key value violates unique constraint \"accounts_email_key\""
                :postgresql.error/detail "Key (email)=(test@example.com) already exists."
                :postgresql/sql-state "23505"
                :postgresql/columns ["email"]
                :postgresql/constraint "accounts_email_key"
                :postgresql/error :unique-violation}
              (extract-data e)))))))

(deftest foreign-key-failure
  (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
    (try
      (jdbc/execute! t ["INSERT INTO payments (id, account_id) VALUES(?, ?)" uuid-1 uuid-2])
      (is false)
      (catch PSQLException e
        (is (= {:postgresql.error/message "Insert or update on table \"payments\" violates foreign key constraint \"payments_account_id_fkey\""
                :postgresql.error/detail "Key (account_id)=(91f47e99-d616-4d8c-9c02-cbd13bceac60) is not present in table \"accounts\"."
                :postgresql/sql-state "23503"
                :postgresql/columns ["account_id"]
                :postgresql/constraint "payments_account_id_fkey"
                :postgresql/error :foreign-key-violation
                :postgresql/relation "payments"
                :postgresql.relation/foreign "accounts"
                }
              (extract-data e)))))))

(deftest foreign-key-parent-delete
  (jdbc/with-transaction [t th/db-uri {:rollback-only true}]
    (try
      (jdbc/execute! t ["INSERT INTO accounts (id, email, balance) VALUES(?, ?, 1)" uuid-1 email-1])
      (jdbc/execute! t ["INSERT INTO accounts (id, email, balance) VALUES(?, ?, 1)" uuid-2 email-2])
      (jdbc/execute! t ["INSERT INTO payments (id, account_id) VALUES(?, ?)" uuid-1 uuid-2])
      (jdbc/execute! t ["DELETE FROM accounts"])
      (is false)
      (catch PSQLException e
        (is (= {:postgresql.error/message "Update or delete on table \"accounts\" violates foreign key constraint \"payments_account_id_fkey\" on table \"payments\""
                :postgresql.error/detail "Key (id)=(91f47e99-d616-4d8c-9c02-cbd13bceac60) is still referenced from table \"payments\"."
                :postgresql/sql-state "23503"
                :postgresql/columns ["id"]
                :postgresql/constraint "payments_account_id_fkey"
                :postgresql/error :foreign-key-violation
                :postgresql/relation "accounts"
                :postgresql.relation/foreign "payments"}
              (extract-data e)))))))