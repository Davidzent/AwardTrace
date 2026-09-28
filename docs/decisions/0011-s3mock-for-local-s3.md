# 0011: S3Mock for local and test S3

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

Local development and the Testcontainers integration tests need an S3 API, and AwardTrace uses no other AWS service locally. LocalStack, the usual choice, now ships as a single image that won't start without an auth token tied to a LocalStack account, and its free plan covers non-commercial use only.

## Decision

Use Adobe S3Mock as the S3 endpoint in the local Compose stack and in integration tests. Production uses Amazon S3. The application reaches both through the AWS SDK, with only the endpoint changing.

## Consequences

- No account, token, or CI secret, and an Apache 2.0 license with no usage restrictions.
- One small container that starts in seconds, with a Testcontainers module.
- S3Mock emulates S3 only. Emulating another AWS service locally needs a new decision.
- An emulator can differ from Amazon S3 at the edges, so the rebuild drill against real S3 stays the final check.

## Alternatives considered

| Alternative | Why not |
|---|---|
| LocalStack with a free auth token | An account and a CI secret for one emulated service, under a non-commercial license |
| MinIO | The `minio/minio` image is no longer published on Docker Hub |
| Amazon S3 in tests | Tests would need AWS credentials and network access, and would cost money |
