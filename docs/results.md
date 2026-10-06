# Results

Every number on this page was measured on production or on the gold set, and each section says when and how. A goal without a measurement is listed under [Not measured yet](#not-measured-yet).

## Search under load

Measured on 2026-10-06 against production: one `c7i-flex.large` host holding the Department of Agriculture's 31,880 awards. k6 2.2.0 ran each scenario in [loadtest/](../loadtest/) from a home connection outside AWS, ramping to 20 requests a second over a minute and holding that rate for five. The load generator's address was exempt from the rate limits for the run only.

| Scenario | Requests | Failed | Median | p95 | Target p95 |
|---|---|---|---|---|---|
| Search, `search.js` | 6,629 | 0 | 140 ms | 155 ms | 300 ms |
| Award detail, `detail.js` | 6,630 | 0 | 68 ms | 134 ms | 150 ms |

Every time includes the network: a TCP connection from the load generator to the host took 73 ms, and the fastest search took 133 ms.

`mixed.js` sent 6,629 requests, 70% searches, 25% award details, and 5% recipients, and none failed. Its searches had a p95 of 147 ms, and its award details 132 ms. The scenario is meant to run while a delta file ingests, but USAspending hadn't published an Agriculture file newer than the one loaded on 2026-10-05, so no ingest ran during it.

Across the three scenarios, 0 of 19,888 requests failed, against a target of under 0.1%.

## Classification

Each row scores one model and prompt version on the gold set in `eval/`, against the free PSC baseline (ADR 0007). A model's categories become the site's default only if they beat the baseline. Each model links to its full report.

| Classifier | Prompt | Accuracy | Accuracy on clear rows | Answered UNCLASSIFIABLE | Agrees with baseline | Cost per 100 | Run |
|---|---|---|---|---|---|---|---|
| PSC baseline | - | 54.0% | 59.7% | 0.0% | - | Free | 2026-10-05 |
| [claude-haiku-4-5](../eval/results/claude-haiku-4-5-v1.md) | v1 | 66.5% | 65.7% | 12.5% | 43.0% | $0.0210 | 2026-10-05 |
| [claude-opus-5-5](../eval/results/claude-opus-5-5-v1.md) | v1 | 76.5% | 77.9% | 6.5% | 48.5% | $0.1178 | 2026-10-05 |

## Not measured yet

| Goal | Measured by |
|---|---|
| Rebuild time, pipeline throughput, and freshness | The rebuild drill: destroy production, recreate it with Terraform, and replay the files in S3 |
| A replay reproduces identical counts and checksums | The same drill, comparing the database before and after |
| Monthly cost | AWS Billing for October 2026, once the month closes |
