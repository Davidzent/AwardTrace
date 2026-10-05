-- The model's category for each distinct description (docs 04 and 09), keyed by the description's hash, so a
-- description that several awards share is classified once. Awards refer to it by value, with no foreign key, because
-- an award's description may not be classified yet. An UNCLASSIFIABLE answer always says why, VAGUE, REFUSAL, or
-- FAILED, and no other category carries a reason.
CREATE TABLE classification (
    description_hash  text PRIMARY KEY CHECK (char_length(description_hash) = 64),
    category          text NOT NULL REFERENCES taxonomy_category (code),
    confidence        numeric(3, 2) CHECK (confidence BETWEEN 0 AND 1),
    reason_code       text CHECK (reason_code IN ('VAGUE', 'REFUSAL', 'FAILED')),
    model             text NOT NULL,
    prompt_version    text NOT NULL,
    classified_at     timestamptz NOT NULL DEFAULT now(),
    CHECK ((category = 'UNCLASSIFIABLE') = (reason_code IS NOT NULL))
);
