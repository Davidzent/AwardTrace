# 0005: Relationships in PostgreSQL, not Neo4j

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

The network view shows the primes above and the subrecipients below a recipient. Reported subawards are one level deep: a prime and its first-tier subcontractors.

## Decision

Store subawards in PostgreSQL and serve the network from a materialized view of prime-to-sub edges. Paths deeper than one hop are out of scope for v1.

## Consequences

- No extra database to run, back up, or secure.
- Network queries are indexed lookups.
- If a later version wants multi-hop paths, a recursive query with a depth limit covers them. Reconsider a graph database only if that measurably fails.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Neo4j | A second database with a JVM-sized footprint, to answer one-hop questions that an indexed PostgreSQL view already answers |
