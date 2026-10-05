# 0013: Classifier model and default category

| | |
|---|---|
| Status | Accepted. The decision rules were committed before any evaluation run; the results and the decision follow them |
| Date | 2026-10-05 |

## Context

The classifier can run on Claude Haiku 4.5 or Claude Opus 5.5 (doc 09). For this task, Opus costs about 4 to 5 times as much, since it always thinks and thinking bills as output: about $25 against $6 to classify the Department of Agriculture's descriptions in batches. The site shows the classifier's category only if it beats the free PSC baseline ([ADR 0007](0007-llm-enrichment-is-optional-and-evaluated.md)). The evaluation scores both models and the baseline on 200 hand-labeled descriptions, `eval/gold.csv`. Accuracy on the 181 rows not labeled `UNCLASSIFIABLE` is the fair comparison, since the baseline never answers `UNCLASSIFIABLE`.

## Decision rules

Fixed before the runs, so the results can't shape them:

1. Haiku 4.5 classifies every description. Opus 5.5 runs once on the gold set, as a ceiling that shows how much accuracy the cheaper model gives up; its score doesn't change the model.
2. The classifier's category becomes the site's default, `awardtrace.categories.default-source: llm`, only if Haiku's accuracy on the clear rows beats the baseline's by at least 3 points. Otherwise the site keeps the baseline, and the write-up says so.

## Results

Both models ran prompt v1 on the gold set on 2026-10-05, 25 descriptions a request. [Classification results](../results.md) keeps the running table, and each report has the confusion matrix and every miss.

| Classifier | Accuracy | Accuracy on the 181 clear rows | Answered `UNCLASSIFIABLE` | Cost per 100, synchronous | Report |
|---|---|---|---|---|---|
| PSC baseline | 54.0% | 59.7% (108) | Never | Free | - |
| Claude Haiku 4.5 | 66.5% | 65.7% (119) | 12.5% | $0.021 | [claude-haiku-4-5-v1](../../eval/results/claude-haiku-4-5-v1.md) |
| Claude Opus 5.5 | 76.5% | 77.9% (141) | 6.5% | $0.118 | [claude-opus-5-5-v1](../../eval/results/claude-opus-5-5-v1.md) |

- Haiku beats the baseline by 6.1 points on the clear rows, 11 descriptions.
- Opus beats Haiku by 12.2 points, at 5.6 times the cost per description.
- Haiku's most common miss is `SUPPLIES_EQUIPMENT` for an order whose domain has its own category: 16 of its 67 misses, including all 4 firearms and ammunition orders labeled `DEFENSE_SYSTEMS`. The baseline, which reads the product code, gets 11 of the 16 right.

## Decision

Haiku 4.5 classifies every description (rule 1). Its 6.1-point lead clears the 3 points rule 2 requires, so the classifier's category becomes the site's default: `awardtrace.categories.default-source: llm`.

## Consequences

- Classifying Agriculture's roughly 61,000 descriptions costs about $6.40 on Haiku with batches, at the measured price. Opus would cost about $36, not the $25 estimated above.
- Haiku's lead isn't statistically significant. Of the 73 clear rows where exactly one of the two is right, Haiku has 42 and the baseline 31, and an exact McNemar test gives p = 0.24. Opus's lead over the baseline is significant: 54 to 21, p < 0.001. Rule 2 set a margin, not a significance test, so the default follows it.
- Production holds no classifications yet. The `llm` default ships before the first backfill, so each stored classification's `CLASSIFICATION` event reindexes its awards, and no full reindex is needed.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Switch to Opus if it scores more than 5 or 10 points higher | About $20 more for Agriculture, and several times more on every later delta, for a gain the baseline comparison may not need |
| Decide the rules after seeing the scores | Any margin chosen after the fact can bend toward the hoped-for answer, and nothing could show it didn't |
