package com.zntsns.awardtrace.search.internal;

import static com.zntsns.awardtrace.AwardRows.saveAward;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.ElasticsearchTestConfiguration;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.shared.SearchIndexes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Without the indexer, the index stays empty while the database holds an award: the gap the page exists to show. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class})
class StatusControllerIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ElasticsearchClient elasticsearch;

    @AfterEach
    void cleanUp() throws Exception {
        AwardRows.emptyTables(jdbc);
        jdbc.sql("TRUNCATE ingest_run CASCADE").update();
        elasticsearch.indices().delete(request -> request.index("awards-v1"));
    }

    @Test
    void reportsIngestIndexAndFreshnessFromOneCachedSnapshot() throws Exception {
        saveAward(jdbc, "CONT_AWD_LIVE", 1, "55000.00", false);
        saveAward(jdbc, "CONT_AWD_DELETED", 2, "0.00", true);
        jdbc.sql("""
                INSERT INTO ingest_run (mode, status, started_at, finished_at, records_published) VALUES
                    ('backfill', 'succeeded', TIMESTAMPTZ '2026-09-06 06:00Z', TIMESTAMPTZ '2026-09-06 06:40Z', 105419),
                    ('delta', 'failed', TIMESTAMPTZ '2026-09-27 06:00Z', TIMESTAMPTZ '2026-09-27 06:02Z', 0)
                """).update();
        elasticsearch.indices().create(request -> request.index("awards-v1")
                .aliases(SearchIndexes.AWARDS, alias -> alias));

        var result = mvc.get().uri("/api/v1/status").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-cache");
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.ingest.last_run.mode").isEqualTo("delta");
        json.extractingPath("$.ingest.last_run.status").isEqualTo("failed");
        json.extractingPath("$.ingest.last_success_at").isEqualTo("2026-09-06T06:40:00Z");
        json.extractingPath("$.index.available").isEqualTo(true);
        json.extractingPath("$.index.alias_target").isEqualTo("awards-v1");
        json.extractingPath("$.index.document_count").isEqualTo(0);
        json.extractingPath("$.index.award_row_count").isEqualTo(1);
        json.extractingPath("$.freshness.latest_source_modified_at").isNotNull();

        saveAward(jdbc, "CONT_AWD_NEWER", 1, "10.00", false);

        assertThat(mvc.get().uri("/api/v1/status").exchange()).bodyJson()
                .extractingPath("$.index.award_row_count").isEqualTo(1);
    }
}
