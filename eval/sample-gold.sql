-- Samples the 200-item gold set (doc 09) from a loaded database, as CSV with an empty label column:
--   docker compose -f infra/compose/compose.yml -f infra/compose/compose.local.yml exec -T postgres \
--     psql -q -U awardtrace -d awardtrace < eval/sample-gold.sql > eval/gold.csv
-- The committed gold.csv came from the Department of Agriculture's FY2025 and FY2026 files.
--
-- One row per distinct description. 180 rows spread evenly across the 13 baseline categories, taking each category's
-- PSC codes in turn so no one code dominates, and 20 terse rows, whose descriptions have at most one word of three or
-- more letters, also spread across PSC codes. Some terse rows are bare codes and others name a thing in a word or two,
-- so together they test where "Not enough information" begins. Hashing with a fixed seed makes the sample repeatable,
-- and the rows are shuffled so the labeler doesn't see them grouped by category.
COPY (
    WITH described AS (
        SELECT DISTINCT ON (description_hash)
               description_hash, description, psc_code, psc_baseline_category(psc_code) AS baseline,
               (SELECT count(*) FROM regexp_matches(description, '[A-Za-z]{3,}', 'g')) AS words,
               md5(description_hash || 'gold-v1') AS draw
        FROM award
        WHERE deleted_at IS NULL AND description IS NOT NULL AND psc_code IS NOT NULL
        ORDER BY description_hash, award_id
    ),
    clear AS (
        SELECT *, row_number() OVER (PARTITION BY baseline, psc_code ORDER BY draw) AS turn
        FROM described
        WHERE words >= 2
    ),
    clear_ranked AS (
        SELECT *, row_number() OVER (PARTITION BY baseline ORDER BY turn, draw) AS place
        FROM clear
    ),
    vague AS (
        SELECT *, row_number() OVER (PARTITION BY psc_code ORDER BY draw) AS turn
        FROM described
        WHERE words <= 1
    ),
    picked AS (
        -- The two smallest categories give 13 rows each, so the clear rows total 180.
        SELECT description_hash, description, psc_code
        FROM clear_ranked
        WHERE place <= CASE WHEN baseline IN ('CYBERSECURITY', 'OTHER') THEN 13 ELSE 14 END
        UNION ALL
        (SELECT description_hash, description, psc_code FROM vague ORDER BY turn, draw LIMIT 20)
    )
    SELECT description_hash, description, psc_code, NULL::text AS label
    FROM picked
    ORDER BY md5(description_hash || 'gold-v1-order')
) TO STDOUT WITH (FORMAT csv, HEADER);
