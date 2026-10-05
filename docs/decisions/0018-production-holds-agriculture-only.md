# 0018: Production holds the Department of Agriculture only

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-04 |

## Context

The plan was to ingest every agency unless sizing ruled it out. Phase 0 measured FY2025 and FY2026 for all agencies at about 11 million transactions, 3.3 GB of zipped archives, and one month's delta at 2.12 GB. The host has 2 vCPUs, 4 GiB of memory, and a 40 GiB disk, and Kafka keeps a 7-day copy of every transaction it receives. The AWS free plan's credits pay for it ([ADR 0015](0015-x86-host-on-the-aws-free-plan.md)). The Department of Agriculture's 61,204 awards already run through the whole pipeline locally, and the Phase 5 gold set comes from them.

## Decision

Production ingests the Department of Agriculture, toptier code `012`, and no other agency. `AWARDTRACE_INGEST_AGENCIES` in `infra/compose/compose.prod.yml` sets it, and the backfill, the weekly delta, and subawards all follow it.

## Consequences

- The backfill downloads about 36 MB of archives and runs in minutes, with no larger instance.
- Search, recipients, and categories cover one agency, which the public docs must state.
- Classifying every description cost $3.25 on Claude Haiku 4.5 with Message Batches ([ADR 0013](0013-classifier-model-and-default-category.md)).
- Widening the scope means adding agency codes and running a backfill for them. Measure disk use and duration on production first.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Every agency | Hours of ingest on 2 vCPUs, and Kafka's 7-day copy alone could fill much of the disk |
| The ten largest agencies | Barely smaller: the Department of Defense alone is 750 MB of FY2026's 1.38 GB |
| A few mid-size agencies | Possible later, once production's bytes per award are measured |
