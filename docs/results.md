# Classification results

Each row scores one model and prompt version on the gold set in `eval/`, against the free PSC baseline (ADR 0007). A model's categories become the site's default only if they beat the baseline. Each model links to its full report.

| Classifier | Prompt | Accuracy | Accuracy on clear rows | Answered UNCLASSIFIABLE | Agrees with baseline | Cost per 100 | Run |
|---|---|---|---|---|---|---|---|
| PSC baseline | - | 54.0% | 59.7% | 0.0% | - | Free | 2026-10-05 |
| [claude-haiku-4-5](../eval/results/claude-haiku-4-5-v1.md) | v1 | 66.5% | 65.7% | 12.5% | 43.0% | $0.0210 | 2026-10-05 |
| [claude-opus-5-5](../eval/results/claude-opus-5-5-v1.md) | v1 | 76.5% | 77.9% | 6.5% | 48.5% | $0.1178 | 2026-10-05 |
