# 0008: JDBC for writes, JPA for reads

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

The pipeline performs bulk, version-guarded upserts that depend on `RETURNING`. The API performs straightforward reads of award and recipient aggregates.

## Decision

Write with Spring `JdbcClient` and explicit SQL in batches. Read with Spring Data JPA entities mapped to the same tables, with `ddl-auto=validate`.

## Consequences

- Writes are fast, and their semantics are visible in one SQL statement.
- Reads stay concise.
- Two access styles exist, separated by module: only `pipeline`, `outbox`, and `enrichment` use `JdbcClient`.

## Alternatives considered

| Alternative | Why not |
|---|---|
| JPA for everything | Conditional upserts with `RETURNING` fight the ORM, and batch writes need careful tuning |
| JDBC for everything | Verbose read code for no gain |
| jOOQ | Type-safe SQL for both paths, but it adds a code-generation step to the build for reads that are simple entity lookups |
