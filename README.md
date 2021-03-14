# pgerrors

Small utility library to extract data from PostgreSQL errors.

## Usage

`com.verybigthings.pgerrors/extract-data` function accepts `org.postgresql.util.PSQLException` and returns a map with data extracted from the error message.

```clojure
(com.verybigthings.pgerrors/extract-data e)
=> {:postgresql.error/message "Duplicate key value violates unique constraint \"accounts_pkey\""
    :postgresql.error/detail "Key (id)=(3c7d2b4a-3fc8-4782-a518-4ce9efef51e7) already exists."
    :postgresql/sql-state "23505"
    :postgresql/columns ["id"]
    :postgresql/constraint "accounts_pkey"
    :postgresql/error :unique-violation}
```

Extracted data varies between the errors, but at minimum, `:postgresql.error/sql-state`, `:postgresql/error` and `:postgresql.error/message` keys will be present.

## License

Copyright © 2021 VeryBigThings

Distributed under the MIT License.
