# 0012: Awards are projections of their transactions

| | |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |

## Context

The original write rules versioned each award by the `last_modified_date` of whichever transaction arrived last. A check of real source files (the Department of Agriculture FY2026 full file generated 2026-09-09, and the delta generated 2026-09-08) showed that three assumptions behind those rules are wrong:

- The award-level columns on a transaction row describe the award as of that transaction. `total_dollars_obligated` is a running total: it follows the action order in 2,436 of the 2,609 awards whose total changes within the file.
- `last_modified_date` doesn't record when USAspending last changed a row. The September delta re-emits 64,646 of its 80,039 rows with dates before August 2026.
- Corrections carry no marker. Deletions arrive as rows with `correction_delete_ind = D` that hold only the transaction key and a date, with no award key.

## Decision

1. A transaction row's version is the source file it came from, compared as (file date, file name). A row from a newer file replaces the stored row. A row from the same file or an older one changes nothing. Ingest publishes files in file-date order, including during replay.
2. A delete marks the stored transaction as deleted instead of removing it, so a replayed older file can't bring it back. A delete for a transaction that was never stored is ignored. The transaction key encodes the award key, so every delete is keyed, and ordered, with its award's other events.
3. After each batch, the pipeline recomputes every affected award from its live transactions. Award-level fields come from the latest transaction by (`action_date`, `modification_number`, `transaction_id`), and counts and dates come from all of them. `index_version` increases only when the recomputed row differs from the stored one. An award with no live transactions is marked deleted.
4. Rows for indefinite delivery vehicles (IDVs) and for actions before FY2025 are skipped at ingest and counted. They are out of scope, not errors, so they never reach the dead-letter topic.

## Consequences

- Out-of-order delivery and replays can't change the final state. An award is a function of its stored transactions.
- A re-emitted row with unchanged content produces no outbox event, so a monthly delta reindexes only awards that changed.
- `award_transaction` stores each row's award-level columns. The table is wider, but a deleted latest transaction can hand the award back to the one before it.
- `total_obligated` is the running total from the latest action. It includes obligations made before FY2025, which AwardTrace doesn't ingest, so it can differ from the sum of stored transactions. The reconciliation job reports that difference rather than treating it as an error.
- The archive keeps only the latest monthly delta, so ingest must run every month. A missed month is repaired by ingesting the fiscal year's full file. A transaction deleted during a missed month stays live, because full files contain no delete rows.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Version awards by `last_modified_date` | A late correction to an early modification would overwrite the award with an older running total, and rows re-emitted with unchanged dates would never apply |
| Compute `total_obligated` as the sum of stored obligations | Undercounts every award that began before FY2025 |
| Delete transactions outright | A replayed older file would bring deleted transactions back |
