-- The baseline rule from V4 in one place, so the indexer and the award detail can't disagree: the category of the
-- longest psc_baseline_map prefix that starts the PSC, or null without a PSC. A migration that changes the map must
-- also bump the index_version of every award it recategorizes, so search documents and detail ETags follow.
CREATE FUNCTION psc_baseline_category(psc text) RETURNS text
    LANGUAGE sql STABLE PARALLEL SAFE
    RETURN (
        SELECT category FROM psc_baseline_map
        WHERE starts_with(psc, psc_prefix)
        ORDER BY char_length(psc_prefix) DESC
        LIMIT 1
    );
