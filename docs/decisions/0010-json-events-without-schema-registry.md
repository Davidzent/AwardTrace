# 0010: JSON events without a schema registry

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

Events need a stable, validated shape. The common production answer is Avro with a schema registry, which is another always-on service on an already full host.

## Decision

Serialize events as JSON with a `schema_version` field. Commit a JSON Schema for each event type and version, and validate every event type against it in a contract test.

## Consequences

- Events on the topic are human-readable, which makes debugging easy.
- No extra container.
- The broker doesn't enforce compatibility; tests do. A producer bug could still publish a bad event, and the consumer's validation sends it to the DLT.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Avro with Confluent Schema Registry | Stronger guarantees and smaller messages, at the cost of memory the host doesn't have |
| Protocol Buffers | The same tradeoff |
