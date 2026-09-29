package com.zntsns.awardtrace.indexer.internal;

import static com.zntsns.awardtrace.indexer.internal.IndexerTestData.saveAward;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zntsns.awardtrace.ElasticsearchTestConfiguration;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.indexer.internal.Reindexer.Summary;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("indexer")
@Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class})
class ReindexIT {

    @Autowired
    Reindexer reindexer;

    @Autowired
    ElasticsearchClient elasticsearch;

    @Autowired
    JdbcClient jdbc;

    /** Puts the alias back on the first index and drops rebuilt ones, for the other tests sharing this cluster. */
    @AfterEach
    void restoreIndexes() throws Exception {
        IndexerTestData.emptyTables(jdbc);
        var rebuilt = elasticsearch.indices().get(request -> request.index(AwardsIndex.MAPPING + "-*")).indices()
                .keySet();
        elasticsearch.indices().updateAliases(request -> request.actions(action -> action
                .add(add -> add.index(AwardsIndex.MAPPING).alias(AwardsIndex.ALIAS))));
        for (String index : rebuilt) {
            elasticsearch.indices().delete(request -> request.index(index));
        }
    }

    @Test
    void rebuildsIntoANewIndexMovesTheAliasAndCatchesUpOnChangesMadeDuringTheRebuild() throws Exception {
        saveAward(jdbc, "CONT_AWD_REINDEX_A", 2, "27500.00", false);
        saveAward(jdbc, "CONT_AWD_REINDEX_B", 5, "55000.00", false);
        saveAward(jdbc, "CONT_AWD_REINDEX_GONE", 3, "0.00", true);
        // An outbox row dated after the rebuild starts stands in for a change committed while it runs.
        jdbc.sql("""
                INSERT INTO outbox (aggregate_id, event_type, change_reason, index_version, created_at)
                VALUES ('CONT_AWD_REINDEX_B', 'AwardChanged', 'TRANSACTION', 5, now() + interval '1 minute')
                """).update();

        Summary summary = reindexer.reindex();

        assertThat(summary.index()).startsWith(AwardsIndex.MAPPING + "-");
        assertThat(summary.documents()).isEqualTo(2);
        assertThat(summary.caughtUp()).isEqualTo(1);
        assertThat(elasticsearch.indices().getAlias(request -> request.name(AwardsIndex.ALIAS)).aliases())
                .containsOnlyKeys(summary.index());
        assertThat(elasticsearch.indices().exists(request -> request.index(AwardsIndex.MAPPING)).value())
                .as("the old index is kept").isTrue();
        var document = elasticsearch.get(request -> request.index(AwardsIndex.ALIAS).id("CONT_AWD_REINDEX_B"),
                Map.class);
        assertThat(document.version()).isEqualTo(5);
        assertThat(elasticsearch.indices().getSettings(request -> request.index(summary.index()))
                .get(summary.index()).settings().index().refreshInterval().time())
                .as("refresh is restored after the load").isEqualTo("30s");
    }
}
