# 0014: Subawards from USAspending's bulk download API

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-29 |

## Context

The recipient network needs reported subawards: which prime recipients subcontract to whom, and for how much. The monthly award data archive that supplies contract transactions holds only Contracts and Assistance files, so subawards need another source.

USAspending offers two:

- `POST /api/v2/bulk_download/awards/` with `sub_award_types: ["procurement"]` generates a zipped CSV of every subaward for an agency and a date range of at most one year, and returns a status URL to poll.
- `POST /api/v2/subawards/` pages through the subawards of one prime award at a time.

A probe for the Department of Agriculture on 2026-09-29 generated its FY2025 and FY2026 files in under ten seconds each: 394 subawards in 118 columns, 118.8 KB zipped. `subaward_sam_report_id` is a UUID, unique across both files. `subaward_sam_report_last_modified_date` is set on every row. `prime_award_unique_key` uses the same award ID format as the contract files, and 129 of the 139 prime awards it names were already loaded. The rest were 2 IDVs and 8 contracts whose last action predates scope.

## Decision

Ingest subawards from the bulk download API. For each agency and fiscal year in scope, request one generated file, poll until it finishes, download it, and store it in S3 under `raw/subawards/{fiscal_year}/{sha256}.zip` with an `ingest_file` row, exactly like a contract file. From there, parsing and publishing work the same way, and replay rebuilds subawards from S3 alone (ADR 0002).

A subaward's key is `subaward_sam_report_id`, and its version is `subaward_sam_report_last_modified_date`. A subaward refers to its prime award by ID with no foreign key, so subawards of out-of-scope primes still feed the network.

## Consequences

- One request per agency and fiscal year, rather than one per prime award.
- Generated files are regenerated on every run and embed a timestamp, so their bytes and SHA-256 change even when no subaward did. Each run stores and publishes a new copy. The version guard makes republishing harmless, and the files are small.
- Generation is asynchronous and can fail or stall on USAspending's side. Ingest polls with a deadline and fails the run rather than waiting forever.
- Reported subawards are sparse: 394 for an agency with 61,204 contract awards. Every subaward surface says "reported subawards".
- The API's one-year limit on date ranges matches fiscal-year files, so it costs nothing.

## Alternatives considered

| Alternative | Why not |
|---|---|
| `POST /api/v2/subawards/` per prime award | Tens of thousands of paged requests per agency against a rate-limited API, with no file to store and replay |
| SAM.gov subaward reporting API | Needs an API key, and USAspending already republishes the same reports |
| Leaving subawards out | The recipient network is part of the plan's Should scope |
