# 0015: x86 host on the AWS free plan

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-04 |

## Context

The AWS account is on the free plan: $100 in credits, for at most six months, and no card charges. The free plan launches only free-tier instance types, and refuses `t4g.medium` with `InvalidParameterCombination`. Its arm64 types, `t4g.micro` and `t4g.small`, have 2 GiB of memory or less, and the stack needs about 3.5 GiB (doc 10).

## Decision

The host runs on `c7i-flex.large`: x86, 2 vCPUs, and 4 GiB. The one-time backfill runs on `m7i-flex.large`, with 8 GiB. Images are built for `linux/amd64`.

## Consequences

- Nothing is charged to a card. The host costs about $70 a month in credits instead of about $33, so $100 lasts about six weeks.
- When the credits run out, AWS suspends the account and closes it 90 days later unless it is upgraded. The raw bucket goes with it, so upgrade, or copy `raw/` elsewhere, before then ([ADR 0002](0002-s3-raw-as-source-of-truth.md)).
- Flex instances have no CPU credits, so there are no surplus charges to guard against.
- Images build natively on GitHub's x86 runners.
- After an upgrade to the paid plan, returning to `t4g.medium` changes only the instance types, the AMI filter, the Compose download in the host's user data, and the image platform.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Upgrade to the paid plan and keep `t4g.medium` | Half the cost, but the account can then charge a card. Deferred, not rejected |
| `t4g.small` on the free plan | 2 GiB; the stack doesn't fit |
| Oracle Cloud Always Free, Arm with 12 GB | Free with no end date, but replaces every AWS-specific part: Terraform, the registry, the deploy, secrets, and logs |
