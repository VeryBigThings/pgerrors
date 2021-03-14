CREATE SCHEMA public;

CREATE TYPE account_status AS enum ('active', 'trial');

CREATE TABLE accounts
(
    id      UUID PRIMARY KEY,
    email   TEXT           NOT NULL UNIQUE,
    balance SMALLINT CHECK (balance >= 0),
    status  account_status NOT NULL DEFAULT ('active'),
    data JSONB
);

CREATE TABLE payments
(
    id         UUID PRIMARY KEY,
    account_id UUID REFERENCES accounts
);

CREATE TABLE two_col_uniqueness (
    str_1 TEXT,
    str_2 TEXT
);

CREATE UNIQUE INDEX two_col_uniqueness_str_1_str_2 ON two_col_uniqueness(str_1,str_2);

CREATE TABLE two_not_null (
    str_1 TEXT NOT NULL,
    str_2 TEXT NOT NULL
);