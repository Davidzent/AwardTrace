# 0006: Single-node infrastructure

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

The budget is about $35 a month. Managed Kafka, managed search, and a NAT gateway each cost as much as the whole budget or more.

## Decision

Run Kafka, PostgreSQL, Elasticsearch, the application, and Caddy on one `t4g.medium` with Docker Compose. Each stateful service is a single node, with a replication factor of 1 in Kafka and zero replicas in Elasticsearch.

## Consequences

- Cheap and simple to operate.
- No high availability. Losing the host means a rebuild, which [ADR 0002](0002-s3-raw-as-source-of-truth.md) makes routine.
- The highly available equivalent is three Kafka brokers with a replication factor of 3 and `min.insync.replicas=2`, a three-node search cluster, and managed PostgreSQL with a standby. Moving to it changes configuration, not application code.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Amazon MSK, Amazon OpenSearch Service, Amazon RDS | The right choice at a funded company, and many times this budget |
| Kubernetes | Adds a control plane to a one-host system |
