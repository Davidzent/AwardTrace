import type { Schemas } from '../../api/client';

/** Every status tile states its condition in a word as well as in color (doc 08). */
export type Condition = 'OK' | 'Degraded' | 'Down' | 'Unknown';

const MINUTE = 60_000;
const DAY = 24 * 60 * MINUTE;

/** A failed run leaves the data served but stale. */
export function ingestCondition(ingest: Schemas['IngestStatus'] | undefined): Condition {
  const run = ingest?.last_run;
  if (!run) {
    return 'Unknown';
  }
  return run.status === 'failed' ? 'Degraded' : 'OK';
}

/**
 * Dead letters need a person to look at them, and an event that has waited in the outbox for five minutes means the
 * relay is stuck. Consumer lag alone is normal during a backfill.
 */
export function pipelineCondition(pipeline: Schemas['PipelineStatus'] | undefined, now: number): Condition {
  if (!pipeline) {
    return 'Unknown';
  }
  if (!pipeline.available) {
    return 'Down';
  }
  const oldest = pipeline.outbox_backlog?.oldest_created_at;
  const stuck = oldest ? now - Date.parse(oldest) > 5 * MINUTE : false;
  return (pipeline.dead_letters ?? 0) > 0 || stuck ? 'Degraded' : 'OK';
}

/** Search serves every live award exactly when the counts match; otherwise the index is behind or ahead. */
export function indexCondition(index: Schemas['IndexStatus'] | undefined): Condition {
  if (!index) {
    return 'Unknown';
  }
  if (!index.available) {
    return 'Down';
  }
  return index.document_count === index.award_row_count ? 'OK' : 'Degraded';
}

/** USAspending republishes monthly, so a newest change older than 45 days means ingest has stopped keeping up. */
export function freshnessCondition(freshness: Schemas['Freshness'] | undefined, now: number): Condition {
  const latest = freshness?.latest_source_modified_at;
  if (!latest) {
    return 'Unknown';
  }
  return now - Date.parse(latest) > 45 * DAY ? 'Degraded' : 'OK';
}
