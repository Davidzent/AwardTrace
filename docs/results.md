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

## Rebuild drill

On 2026-10-06, `terraform destroy` removed production: the host, its volume, the network, the registries, and the roles. Terraform and the raw files in S3 then rebuilt it. The raw bucket has its own Terraform state, so the destroy couldn't reach it.

| Step | Started (UTC) | Finished (UTC) | Took |
|---|---|---|---|
| `terraform apply` creates 42 resources, then the deploy workflow builds, scans, and starts the release | 19:54:00 | 19:58:40 | 4 min 40 s |
| The replay restores 31,344 classifications from their snapshot and publishes the 3 files' 54,741 records | 19:59:29 | 19:59:57 | 28 s |
| The pipeline writes 54,063 transactions | 19:59:34 | 20:00:25 | 51 s |
| Every award is searchable | | 20:00:54 | |
| **From `terraform apply` to every award searchable** | **19:54:00** | **20:00:54** | **6 min 54 s** |

The total includes the gaps between steps, which were started by hand. The DNS record for the new address changed while the deploy ran, so it isn't on the path.

### Throughput

The pipeline wrote 54,063 transactions in 50.6 seconds, 1,068 a second, on the one `c7i-flex.large` host (2 vCPUs, 4 GiB) that also runs Kafka, PostgreSQL, Elasticsearch, and the API.

### The replay reproduced the data exactly

Each checksum is the MD5 of every row's source-derived columns in key order; when a row was written is left out.

| Table | Rows before | Rows after | Checksum before and after |
|---|---|---|---|
| `award` | 31,880 | 31,880 | `cf31d42054a5153ea6608ddef278c88f` |
| `award_transaction` | 54,063 | 54,063 | `fba4b611780e5457013df53568d8759f` |
| `subaward` | 243 | 243 | `e56b81ec9fcae026333231d033071e13` |
| `classification` | 31,344 | 31,344 | `6d05903d8b539359493b0eb910faf4ac` |
| `recipient` | 8,002 | 8,002 | Not taken |

The awards' obligations summed to $21,528,432,467.78 both times. During the replay, Elasticsearch turned away 807 writes because a newer version of the same award was already indexed, which is what its external versioning is for.

### Freshness

| Measure | Result |
|---|---|
| From an award's change to its event on Kafka, over the replay's 42,118 changes | Median 1.17 s, p95 1.70 s, longest 2.11 s |
| From the last transaction written to every award searchable | Under 30 s: written at 20:00:24.7, all searchable by 20:00:54.2 |

The index refreshes every 30 seconds, so an indexed change can wait up to 30 seconds to become searchable. The status endpoint caches its counts for 15 seconds, so the searchable time is accurate to 15 seconds: the index held 15,298 searchable awards at 20:00:39 and all 31,880 at 20:00:54.

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
| Freshness for one change in a weekly delta, p95 under 60 s | A delta ingest of a new USAspending file. The newest one, from September 6, was already loaded. |
| Monthly cost | AWS Billing for October 2026, once the month closes |
