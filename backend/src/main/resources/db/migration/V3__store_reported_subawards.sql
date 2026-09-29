-- Subawards reported by prime recipients (ADR 0014). The key is the SAM.gov report ID and the version its last
-- modification. The prime award is referenced by value, not by foreign key: it may be out of scope, such as an IDV
-- or an older contract, and the relationship between the two recipients still counts.
CREATE TABLE subaward (
    subaward_key          text PRIMARY KEY,
    prime_award_id        text NOT NULL,
    prime_recipient_uei   text NOT NULL CHECK (char_length(prime_recipient_uei) = 12),
    prime_recipient_name  text NOT NULL,
    sub_recipient_uei     text CHECK (char_length(sub_recipient_uei) = 12),
    sub_recipient_name    text NOT NULL,
    subaward_number       text,
    amount                numeric(18, 2) NOT NULL,
    action_date           date NOT NULL,
    description           text,
    source_modified_at    timestamptz NOT NULL,
    ingested_at           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX subaward_prime_award_idx ON subaward (prime_award_id);
CREATE INDEX subaward_prime_recipient_idx ON subaward (prime_recipient_uei);
CREATE INDEX subaward_sub_recipient_idx ON subaward (sub_recipient_uei);

-- The recipient network: one row per prime and subrecipient pair, named as each was named most recently. The unique
-- index lets the pipeline refresh it concurrently, so readers are never blocked.
CREATE MATERIALIZED VIEW recipient_edge AS
SELECT prime_recipient_uei                                                          AS prime_uei,
       (array_agg(prime_recipient_name ORDER BY action_date DESC, subaward_key))[1] AS prime_name,
       sub_recipient_uei                                                            AS sub_uei,
       (array_agg(sub_recipient_name ORDER BY action_date DESC, subaward_key))[1]   AS sub_name,
       count(*)                                                                     AS subaward_count,
       sum(amount)                                                                  AS total_amount,
       min(action_date)                                                             AS first_action_date,
       max(action_date)                                                             AS last_action_date
FROM subaward
WHERE sub_recipient_uei IS NOT NULL
GROUP BY prime_recipient_uei, sub_recipient_uei;

CREATE UNIQUE INDEX recipient_edge_pair_idx ON recipient_edge (prime_uei, sub_uei);
CREATE INDEX recipient_edge_sub_idx ON recipient_edge (sub_uei);
