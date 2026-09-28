# 0002: S3 raw files as the source of truth

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

PostgreSQL and Elasticsearch will change shape as the project evolves, and a single host can be lost. Downloading again from USAspending is slow, puts load on a public service, and isn't guaranteed to return the same files.

## Decision

Every source file is stored in S3 under a content-addressed key before anything reads it. PostgreSQL and Elasticsearch are projections that the `replay` task can rebuild from S3 alone.

Model classifications are the one derived dataset that can't be re-derived: they come from paid calls that can return different answers each run. After each enrichment run, the enricher writes a snapshot of the `classification` table to S3, and `replay` restores it before any new classification runs.

## Consequences

- A schema change becomes a migration plus a replay, not data surgery.
- Recovery is a documented, timed drill rather than a hope.
- A rebuild reproduces the categories the published evaluation measured, and costs nothing in model calls.
- The raw bucket must never be deleted by accident, so it lives in its own Terraform state, outside the environment the rebuild drill destroys.
- Storage costs a few cents a month.

## Alternatives considered

| Alternative | Why not |
|---|---|
| PostgreSQL as the source of truth, with backups | A backup restores a state. A replay re-derives it, which also fixes bugs in the old derivation |
| Kafka with unlimited retention | Costly in disk on a small host, and harder to inspect than files |
