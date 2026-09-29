package com.zntsns.awardtrace.indexer.internal;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the search index from PostgreSQL without downtime. The new index fills while the alias still serves the
 * old one; the alias then moves in one atomic call, and awards that changed during the rebuild are indexed again.
 * The old index is kept, so a bad rebuild can be undone by moving the alias back.
 */
@Component
@Profile("indexer")
class Reindexer {

    private static final Logger log = LoggerFactory.getLogger(Reindexer.class);
    private static final int PAGE_SIZE = 1000;
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    record Summary(String index, long documents, int caughtUp) {
    }

    private final AwardsIndex awardsIndex;
    private final AwardIndexer indexer;
    private final ElasticsearchClient elasticsearch;
    private final JdbcClient jdbc;

    Reindexer(AwardsIndex awardsIndex, AwardIndexer indexer, ElasticsearchClient elasticsearch, JdbcClient jdbc) {
        this.awardsIndex = awardsIndex;
        this.indexer = indexer;
        this.elasticsearch = elasticsearch;
        this.jdbc = jdbc;
    }

    Summary reindex() throws IOException {
        // The database clock, because the catch-up compares it with outbox.created_at.
        OffsetDateTime started = jdbc.sql("SELECT now()").query(OffsetDateTime.class).single();
        String target = AwardsIndex.MAPPING + "-" + started.withOffsetSameInstant(ZoneOffset.UTC).format(SUFFIX);

        awardsIndex.create(target, false);
        Time refreshInterval = elasticsearch.indices().getSettings(request -> request.index(target))
                .get(target).settings().index().refreshInterval();
        setRefreshInterval(target, Time.of(time -> time.time("-1")));
        String after = "";
        List<String> page;
        do {
            page = jdbc.sql("SELECT award_id FROM award WHERE award_id > :after ORDER BY award_id LIMIT :limit")
                    .param("after", after)
                    .param("limit", PAGE_SIZE)
                    .query(String.class)
                    .list();
            if (!page.isEmpty()) {
                indexer.index(page, target);
                after = page.getLast();
            }
        } while (page.size() == PAGE_SIZE);
        setRefreshInterval(target, refreshInterval);
        elasticsearch.indices().refresh(request -> request.index(target));

        long documents = elasticsearch.count(request -> request.index(target)).count();
        long awards = jdbc.sql("SELECT count(*) FROM award WHERE deleted_at IS NULL").query(Long.class).single();
        if (documents != awards) {
            throw new IllegalStateException(target + " holds " + documents + " documents for " + awards
                    + " awards; the alias stays where it is");
        }

        var previous = elasticsearch.indices().getAlias(request -> request.name(AwardsIndex.ALIAS)).aliases().keySet();
        var actions = new ArrayList<Action>();
        previous.forEach(old -> actions.add(Action.of(action -> action.remove(remove -> remove
                .index(old).alias(AwardsIndex.ALIAS)))));
        actions.add(Action.of(action -> action.add(add -> add.index(target).alias(AwardsIndex.ALIAS))));
        elasticsearch.indices().updateAliases(request -> request.actions(actions));

        // Changes committed while the new index filled were written to the old one; write them again.
        List<String> changed = jdbc.sql("SELECT DISTINCT aggregate_id FROM outbox WHERE created_at >= :started")
                .param("started", started)
                .query(String.class)
                .list();
        indexer.index(changed);

        log.info("Rebuilt {} with {} documents; the alias left {}, which is kept until you delete it",
                target, documents, previous);
        return new Summary(target, documents, changed.size());
    }

    private void setRefreshInterval(String index, Time interval) throws IOException {
        elasticsearch.indices().putSettings(request -> request
                .index(index)
                .settings(settings -> settings.refreshInterval(interval)));
    }
}
