package com.zntsns.awardtrace.enrichment.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the model's categories (doc 04, write rule 8). A classification replaces the stored one only when it differs,
 * as when a retry succeeds or a new prompt version answers. Every live award whose description changed category gets
 * a new {@code index_version} and one outbox event in the same transaction, so the indexer picks the category up and a
 * repeated store announces nothing.
 */
@Component
@Profile("enricher")
class ClassificationStore {

    record Result(int stored, int awardsChanged) {
    }

    private static final String UPSERT = """
            INSERT INTO classification (description_hash, category, confidence, reason_code, model, prompt_version)
            VALUES (:descriptionHash, :category, :confidence, :reasonCode, :model, :promptVersion)
            ON CONFLICT (description_hash) DO UPDATE SET
                category = EXCLUDED.category, confidence = EXCLUDED.confidence, reason_code = EXCLUDED.reason_code,
                model = EXCLUDED.model, prompt_version = EXCLUDED.prompt_version, classified_at = now()
            WHERE (classification.category, classification.confidence, classification.reason_code,
                   classification.model, classification.prompt_version)
                IS DISTINCT FROM
                  (EXCLUDED.category, EXCLUDED.confidence, EXCLUDED.reason_code, EXCLUDED.model,
                   EXCLUDED.prompt_version)
            """;

    private static final String BUMP_AWARDS = """
            WITH changed AS (
                UPDATE award SET index_version = index_version + 1, updated_at = now()
                WHERE description_hash IN (:hashes) AND deleted_at IS NULL
                RETURNING award_id, index_version
            )
            INSERT INTO outbox (aggregate_id, event_type, change_reason, index_version)
            SELECT award_id, 'AwardChanged', 'CLASSIFICATION', index_version FROM changed
            """;

    private final NamedParameterJdbcTemplate jdbc;

    ClassificationStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    Result store(List<Classification> classifications) {
        if (classifications.isEmpty()) {
            return new Result(0, 0);
        }
        int[] upserts = jdbc.batchUpdate(UPSERT,
                classifications.stream().map(SimplePropertySqlParameterSource::new).toArray(SqlParameterSource[]::new));
        var changed = new LinkedHashSet<String>();
        IntStream.range(0, upserts.length)
                .filter(i -> upserts[i] > 0)
                .forEach(i -> changed.add(classifications.get(i).descriptionHash()));
        int awardsChanged = changed.isEmpty()
                ? 0
                : jdbc.update(BUMP_AWARDS, new MapSqlParameterSource("hashes", changed));
        return new Result(changed.size(), awardsChanged);
    }
}
