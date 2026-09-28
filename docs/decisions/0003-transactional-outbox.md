# 0003: Transactional outbox

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

When the pipeline changes an award, the indexer and the enricher must hear about it. Writing to PostgreSQL and then publishing to Kafka is two separate writes. A crash between them either loses the event or publishes an event for a change that was rolled back.

## Decision

Write an outbox row in the same database transaction as the award change. A relay publishes outbox rows to `awards.changed.v1` and marks them published after the broker acknowledges them.

## Consequences

- No lost events and no events for rolled-back changes.
- An event can be published twice, so every consumer must be idempotent, which the design already requires.
- The outbox table needs a cleanup job and a partial index on unpublished rows.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Publish after the commit | Loses events on a crash |
| Change data capture with Debezium | Correct, but it needs Kafka Connect, another always-on JVM on a 4 GiB host |
| One transaction spanning the database and Kafka | Not possible across two systems without two-phase commit |
