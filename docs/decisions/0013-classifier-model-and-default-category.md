# 0013: Classifier model and default category

| | |
|---|---|
| Status | Proposed. The decision rules were committed before any evaluation run; the results and the decision follow them |
| Date | 2026-10-05 |

## Context

The classifier can run on Claude Haiku 4.5 or Claude Opus 5.5 (doc 09). For this task, Opus costs about 4 to 5 times as much, since it always thinks and thinking bills as output: about $25 against $6 to classify the Department of Agriculture's descriptions in batches. The site shows the classifier's category only if it beats the free PSC baseline ([ADR 0007](0007-llm-enrichment-is-optional-and-evaluated.md)). The evaluation scores both models and the baseline on 200 hand-labeled descriptions, `eval/gold.csv`. Accuracy on the 181 rows not labeled `UNCLASSIFIABLE` is the fair comparison, since the baseline never answers `UNCLASSIFIABLE`.

## Decision rules

Fixed before the runs, so the results can't shape them:

1. Haiku 4.5 classifies every description. Opus 5.5 runs once on the gold set, as a ceiling that shows how much accuracy the cheaper model gives up; its score doesn't change the model.
2. The classifier's category becomes the site's default, `awardtrace.categories.default-source: llm`, only if Haiku's accuracy on the clear rows beats the baseline's by at least 3 points. Otherwise the site keeps the baseline, and the write-up says so.

## Results

Pending: `eval/results/claude-haiku-4-5-v1.md` and `eval/results/claude-opus-5-5-v1.md`.

## Consequences

- The classifier's cost stays at Haiku's: about $6 for the Agriculture backfill, and cents a week after.
- If Haiku wins, one reindex moves every search document to the classifier's category, keeping the baseline beside it.
- If Haiku doesn't win, its classifications stay stored but unshown, and a later prompt version can be evaluated the same way.
- On 181 rows, a point is about two descriptions, so a margin smaller than 3 points could be noise.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Switch to Opus if it scores more than 5 or 10 points higher | About $20 more for Agriculture, and several times more on every later delta, for a gain the baseline comparison may not need |
| Decide the rules after seeing the scores | Any margin chosen after the fact can bend toward the hoped-for answer, and nothing could show it didn't |
