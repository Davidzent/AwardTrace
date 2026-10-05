# 0016: Natural resources category

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-04 |

## Context

The PSC baseline put group F, natural resources and conservation, in Other. In the Department of Agriculture's data, that made Other 40% of all awards (24,680 of 61,204), and group F was 99.7% of it; forest and range fire suppression (`F003`) alone was 20,869 awards. A residual category that large hides what the awards are, and it flatters any classifier scored on it, since answering Other is right 40% of the time.

## Decision

Add a fourteenth category, `NATURAL_RESOURCES` ("Natural resources and environment"): wildfire suppression, forestry, land and wildlife management, conservation, and environmental cleanup. The baseline maps PSC prefix `F` to it. It is added before the gold set is labeled, so no label changes.

## Consequences

- Other again means a clear description that fits nowhere.
- The facet and the evaluation can tell wildfire and land work apart from everything else.
- Boundary cases need tie-break rules in the labeling guide: cleanup that reads like construction, and firefighting aircraft rental that reads like transport. The PSC puts both in F.
- The classifier prompt grows by one row, about 25 tokens.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Keep Other | 40% of the local data in one uninformative bucket |
| Add it after labeling | Relabels the gold set and reruns the evaluation |
