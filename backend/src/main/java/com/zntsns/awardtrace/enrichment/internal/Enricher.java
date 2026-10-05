package com.zntsns.awardtrace.enrichment.internal;

import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Gives awards their categories (doc 09). A description is classified once, however many awards share it: the
 * {@code classification} table is the cache, so only descriptions it doesn't hold yet go to the model. A
 * {@code FAILED} classification counts as held; {@code enrich-retry-failed} reprocesses those. No database transaction
 * stays open while the model answers; the store opens its own.
 */
@Component
@Profile("enricher")
class Enricher {

    /**
     * @param cached the awards' descriptions that already had a classification
     * @param classified the descriptions sent to the model
     * @param awardsChanged the live awards given a new index version, so the indexer picks their categories up
     */
    record Result(int cached, int classified, int awardsChanged) {
    }

    record Row(String hash, String text, boolean classified) {
    }

    /** One row per distinct description of the live awards, and whether a classification covers it. */
    private static final String DESCRIPTIONS = """
            SELECT DISTINCT ON (a.description_hash) a.description_hash AS hash, a.description AS text,
                   EXISTS (SELECT 1 FROM classification c WHERE c.description_hash = a.description_hash) AS classified
            FROM award a
            WHERE a.award_id IN (:awardIds) AND a.deleted_at IS NULL AND a.description_hash IS NOT NULL
            ORDER BY a.description_hash, a.award_id
            """;

    private final JdbcClient jdbc;
    private final GroupClassifier classifier;
    private final ClassificationStore store;

    Enricher(JdbcClient jdbc, GroupClassifier classifier, ClassificationStore store) {
        this.jdbc = jdbc;
        this.classifier = classifier;
        this.store = store;
    }

    /** Classifies the descriptions of these awards that no classification covers yet, and stores the results. */
    Result enrich(Collection<String> awardIds) {
        if (awardIds.isEmpty()) {
            return new Result(0, 0, 0);
        }
        List<Row> rows = jdbc.sql(DESCRIPTIONS).param("awardIds", awardIds).query(Row.class).list();
        List<Description> uncached = rows.stream()
                .filter(row -> !row.classified())
                .map(row -> new Description(row.hash(), row.text()))
                .toList();
        var stored = store.store(classifier.classify(uncached));
        return new Result(rows.size() - uncached.size(), uncached.size(), stored.awardsChanged());
    }
}
