-- Awards and recipients become projections of their transactions. See docs/decisions/0012.

-- A transaction's version is the source file it came from, compared as (source_file_date, source_file).
-- A delete sets deleted_at instead of removing the row, so a replayed older file can't bring it back.
ALTER TABLE award_transaction
    ADD COLUMN source_file_date        date NOT NULL,
    ADD COLUMN source_file             text NOT NULL,
    ADD COLUMN deleted_at              timestamptz,
    -- The award as of this transaction. The award row is recomputed from its latest live transaction,
    -- so these must be kept per transaction for a delete to hand the award back to the one before it.
    ADD COLUMN piid                    text NOT NULL,
    ADD COLUMN parent_piid             text,
    ADD COLUMN award_type              text NOT NULL,
    ADD COLUMN award_description       text,
    ADD COLUMN awarding_toptier_code   text NOT NULL,
    ADD COLUMN awarding_subtier_code   text,
    ADD COLUMN funding_toptier_code    text,
    ADD COLUMN naics_code              text,
    ADD COLUMN naics_description       text,
    ADD COLUMN psc_code                text,
    ADD COLUMN psc_description         text,
    ADD COLUMN pop_state_code          text,
    ADD COLUMN pop_country_code        text,
    ADD COLUMN total_obligated         numeric(18, 2) NOT NULL,
    ADD COLUMN potential_total_value   numeric(18, 2),
    ADD COLUMN pop_start_date          date,
    ADD COLUMN pop_end_date            date,
    -- The recipient as of this transaction. The recipient row is recomputed the same way.
    ADD COLUMN recipient_uei           text NOT NULL CHECK (char_length(recipient_uei) = 12),
    ADD COLUMN recipient_name          text NOT NULL,
    ADD COLUMN recipient_parent_uei    text CHECK (char_length(recipient_parent_uei) = 12),
    ADD COLUMN recipient_parent_name   text,
    ADD COLUMN recipient_city          text,
    ADD COLUMN recipient_state_code    text,
    ADD COLUMN recipient_country_code  text;

-- Transactions are written before the award they project into, so the award check waits for commit.
ALTER TABLE award_transaction
    ALTER CONSTRAINT award_transaction_award_id_fkey DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX award_transaction_recipient_action_idx
    ON award_transaction (recipient_uei, action_date DESC) WHERE deleted_at IS NULL;

-- The projection replaces the version guard. source_modified_at stays as the newest
-- source_modified_at among the award's live transactions, for display.
ALTER TABLE award
    DROP COLUMN version_transaction_id,
    ADD COLUMN deleted_at timestamptz;

ALTER TABLE recipient
    DROP COLUMN source_modified_at,
    DROP COLUMN first_seen_at,
    DROP COLUMN last_seen_at;
