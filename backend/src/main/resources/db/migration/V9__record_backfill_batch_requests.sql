-- The Message Batch requests the backfill has submitted but not yet stored (doc 09): each request's custom ID and the
-- descriptions it carries, in order. They're recorded before the batch is submitted, and deleted once its results are
-- stored, so a backfill that stops partway finds its batches again on the next run instead of paying for them twice.
-- A row whose batch_id is still null was recorded but never submitted.
CREATE TABLE enrichment_batch_request (
    custom_id           text PRIMARY KEY,
    batch_id            text,
    description_hashes  text[] NOT NULL
);

CREATE INDEX enrichment_batch_request_batch_idx ON enrichment_batch_request (batch_id);
