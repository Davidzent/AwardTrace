# 0007: LLM enrichment is optional and evaluated

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

A plain-language category can make search more useful. But every award already carries a government product code, and model calls cost money and can fail or be refused.

## Decision

Enrichment only adds a category; the PSC-based baseline always exists. The LLM category becomes the default only if it beats the baseline on a hand-labeled gold set, and the comparison is published whatever it shows.

## Consequences

- The site never depends on a third-party API to work.
- The project demonstrates evaluation, not just an API call.
- Labeling 200 items is real work, and it must happen before the model is trusted.

## Alternatives considered

| Alternative | Why not |
|---|---|
| The LLM category as the only category | A single point of failure and an unmeasured claim |
| No LLM at all | Leaves generic descriptions without plain-language categories, and never measures whether a model improves on the government codes |
