-- A fourteenth category for PSC group F, natural resources and conservation, which the baseline put in Other: almost
-- all of Other, and 40% of the Department of Agriculture's awards (ADR 0016). It sorts just before Other.
UPDATE taxonomy_category SET sort_order = 14 WHERE code = 'UNCLASSIFIABLE';
UPDATE taxonomy_category SET sort_order = 13 WHERE code = 'OTHER';
INSERT INTO taxonomy_category (code, label, definition, sort_order) VALUES
    ('NATURAL_RESOURCES', 'Natural resources and environment',
     'Wildfire suppression, forestry, land and wildlife management, conservation, and environmental cleanup', 12);

UPDATE psc_baseline_map SET category = 'NATURAL_RESOURCES' WHERE psc_prefix = 'F';

-- V5's rule: every award this recategorizes gets a new index_version and an outbox event, so its search document
-- and detail ETag follow.
WITH changed AS (
    UPDATE award SET index_version = index_version + 1, updated_at = now()
    WHERE starts_with(psc_code, 'F') AND deleted_at IS NULL
    RETURNING award_id, index_version
)
INSERT INTO outbox (aggregate_id, event_type, change_reason, index_version)
SELECT award_id, 'AwardChanged', 'CLASSIFICATION', index_version FROM changed;
