# 0004: Kafka keyed by award ID

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

Kafka orders messages within a partition only. Without a key, the transactions for one award can land on different partitions and be processed out of order, and two consumers can update the same award row at the same moment.

## Decision

Key every award event by `award_id`, so all of an award's events share a partition and one consumer processes them in order. Guard every upsert on the source version as well.

## Consequences

- No two consumers contend for the same award's row.
- The version guard still protects against reordering at the source and against replays, which partitioning can't prevent.
- The partition count is fixed once chosen, because changing it remaps keys to partitions.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Key by agency | A few very large agencies would create hot partitions |
| No key | No ordering at all, leaving the version guard as the only protection and adding lock contention |
