-- The recipient projection (TransactionWriter) reads all of a recipient's transactions, deleted ones included, so
-- V2's partial index on live rows can't serve it, and every pipeline batch scanned the whole table. No query reads
-- the partial index, so this one replaces it.
DROP INDEX award_transaction_recipient_action_idx;

CREATE INDEX award_transaction_recipient_idx ON award_transaction (recipient_uei);
