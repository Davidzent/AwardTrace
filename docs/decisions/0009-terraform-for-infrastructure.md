# 0009: Terraform for infrastructure

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

The rebuild drill recreates production from nothing, so every AWS resource must be declared in code that can be reviewed, planned, and applied repeatably.

## Decision

Use Terraform, with one module per concern and a single root at `envs/prod`. State lives in S3 with native locking.

## Consequences

- `terraform plan` shows every infrastructure change for review before it is applied, and the same tool works beyond AWS.
- Infrastructure is written in HCL rather than the application's languages.
- The rebuild drill depends on it, so the whole environment must be expressible in it.

## Alternatives considered

| Alternative | Why not |
|---|---|
| AWS CDK | Infrastructure in Java or TypeScript, but AWS-only, and it synthesizes to CloudFormation, which adds a layer to debug |
| CloudFormation directly | Verbose and AWS-only |
| The AWS console | Not reproducible, which makes the rebuild drill impossible |
