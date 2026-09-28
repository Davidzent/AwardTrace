-- Money is numeric(18,2) and may be negative (deobligations). Timestamps are timestamptz in UTC.

CREATE TABLE agency (
    code          text PRIMARY KEY,
    level         text NOT NULL CHECK (level IN ('toptier', 'subtier')),
    name          text NOT NULL,
    abbreviation  text,
    parent_code   text REFERENCES agency (code)
);

CREATE TABLE recipient (
    uei                 text PRIMARY KEY CHECK (char_length(uei) = 12),
    name                text NOT NULL,
    -- No foreign key: the parent may never appear as a recipient.
    parent_uei          text CHECK (char_length(parent_uei) = 12),
    parent_name         text,
    city                text,
    state_code          text,
    country_code        text,
    source_modified_at  timestamptz NOT NULL,
    first_seen_at       timestamptz NOT NULL,
    last_seen_at        timestamptz NOT NULL
);

CREATE TABLE award (
    award_id                text PRIMARY KEY,
    piid                    text NOT NULL,
    parent_piid             text,
    award_type              text NOT NULL CHECK (award_type IN ('A', 'B', 'C', 'D')),
    description             text,
    -- SHA-256 of the normalized description. No foreign key: the classification may not exist yet.
    description_hash        text CHECK (char_length(description_hash) = 64),
    awarding_toptier_code   text NOT NULL REFERENCES agency (code),
    awarding_subtier_code   text REFERENCES agency (code),
    funding_toptier_code    text REFERENCES agency (code),
    recipient_uei           text NOT NULL REFERENCES recipient (uei),
    naics_code              text,
    naics_description       text,
    psc_code                text,
    psc_description         text,
    pop_state_code          text,
    pop_country_code        text,
    total_obligated         numeric(18, 2) NOT NULL,
    potential_total_value   numeric(18, 2),
    pop_start_date          date,
    pop_end_date            date,
    first_action_date       date NOT NULL,
    last_action_date        date NOT NULL,
    -- The federal fiscal year starts October 1, so shifting the date three months forward yields it.
    fiscal_year             smallint GENERATED ALWAYS AS
                                (extract(year FROM last_action_date + interval '3 months')::smallint) STORED,
    -- (source_modified_at, version_transaction_id) is the version the upsert guard compares.
    source_modified_at      timestamptz NOT NULL,
    version_transaction_id  text NOT NULL,
    -- Bumped on every change that must reach search; used as the Elasticsearch external version.
    index_version           bigint NOT NULL,
    transaction_count       integer NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX award_recipient_last_action_idx ON award (recipient_uei, last_action_date DESC);
CREATE INDEX award_description_hash_idx ON award (description_hash);
CREATE INDEX award_awarding_toptier_idx ON award (awarding_toptier_code);

CREATE TABLE award_transaction (
    transaction_id             text PRIMARY KEY,
    award_id                   text NOT NULL REFERENCES award (award_id),
    modification_number        text NOT NULL,
    action_date                date NOT NULL,
    action_type_code           text,
    federal_action_obligation  numeric(18, 2) NOT NULL,
    description                text,
    source_modified_at         timestamptz NOT NULL,
    ingested_at                timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX award_transaction_award_action_idx ON award_transaction (award_id, action_date);

-- Refers to awards by value, not by foreign key, so a pending event never blocks anything.
CREATE TABLE outbox (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       uuid NOT NULL UNIQUE DEFAULT uuidv7(),
    aggregate_id   text NOT NULL,
    event_type     text NOT NULL,
    change_reason  text NOT NULL,
    index_version  bigint NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    published_at   timestamptz
);

-- Keeps relay polling fast as published rows accumulate.
CREATE INDEX outbox_unpublished_idx ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE ingest_run (
    run_id             uuid PRIMARY KEY DEFAULT uuidv7(),
    mode               text NOT NULL,
    status             text NOT NULL,
    started_at         timestamptz NOT NULL DEFAULT now(),
    finished_at        timestamptz,
    files_total        integer NOT NULL DEFAULT 0,
    records_published  bigint NOT NULL DEFAULT 0,
    records_rejected   bigint NOT NULL DEFAULT 0,
    error              text
);

CREATE TABLE ingest_file (
    s3_key        text PRIMARY KEY,
    run_id        uuid NOT NULL REFERENCES ingest_run (run_id),
    source_url    text NOT NULL,
    sha256        text NOT NULL UNIQUE CHECK (char_length(sha256) = 64),
    bytes         bigint NOT NULL,
    row_count     integer,
    status        text NOT NULL,
    published_at  timestamptz
);
