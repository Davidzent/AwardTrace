-- What the enricher spends on the Claude API, per UTC day and path (doc 09): each response's tokens, priced at the
-- published rates when it arrives. The live path reads today's total before every request, so it stays under its
-- daily cap; the backfill sets its own cap per run.
CREATE TABLE enrichment_spend (
    day                 date NOT NULL,
    path                text NOT NULL CHECK (path IN ('live', 'backfill')),
    requests            integer NOT NULL,
    input_tokens        bigint NOT NULL,
    output_tokens       bigint NOT NULL,
    cache_write_tokens  bigint NOT NULL,
    cache_read_tokens   bigint NOT NULL,
    usd                 numeric(12, 6) NOT NULL,
    PRIMARY KEY (day, path)
);
