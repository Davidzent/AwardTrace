package com.zntsns.awardtrace.enrichment.internal;

import static com.zntsns.awardtrace.AwardRows.describe;
import static com.zntsns.awardtrace.AwardRows.saveAward;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.enrichment.internal.Enricher.Result;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("enricher")
@Import({TestcontainersConfiguration.class, FakeClaudeConfiguration.class})
class EnricherCacheIT {

    private static final String SHARED = "5".repeat(64);
    private static final String OTHER = "9".repeat(64);
    private static final List<String> AWARDS = List.of("CONT_AWD_FIRST", "CONT_AWD_SECOND", "CONT_AWD_THIRD");

    @Autowired
    Enricher enricher;

    @Autowired
    FakeClaude claude;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void reset() {
        AwardRows.emptyTables(jdbc);
        claude.reset();
    }

    @Test
    void classifiesADescriptionTwoAwardsShareWithOneRequest() {
        saveAward(jdbc, "CONT_AWD_FIRST", 3, "1000.00", false);
        saveAward(jdbc, "CONT_AWD_SECOND", 7, "2000.00", false);
        describe(jdbc, SHARED, "CONT_AWD_FIRST", "CONT_AWD_SECOND");

        Result result = enricher.enrich(List.of("CONT_AWD_FIRST", "CONT_AWD_SECOND"));

        assertThat(result).isEqualTo(new Result(0, 1, 2));
        assertThat(claude.requests()).containsExactly(List.of(SHARED.substring(0, GroupClassifier.ID_LENGTH)));
        assertThat(jdbc.sql("SELECT category FROM classification WHERE description_hash = :hash")
                .param("hash", SHARED)
                .query(String.class)
                .single())
                .isEqualTo("OTHER");
    }

    @Test
    void sendsOnlyDescriptionsNoClassificationCoversYet() {
        AWARDS.forEach(award -> saveAward(jdbc, award, 1, "1000.00", false));
        describe(jdbc, SHARED, "CONT_AWD_FIRST", "CONT_AWD_SECOND");
        enricher.enrich(List.of("CONT_AWD_FIRST"));
        describe(jdbc, OTHER, "CONT_AWD_THIRD");

        Result result = enricher.enrich(AWARDS);

        assertThat(result).isEqualTo(new Result(1, 1, 1));
        assertThat(claude.requests()).containsExactly(List.of(SHARED.substring(0, GroupClassifier.ID_LENGTH)),
                List.of(OTHER.substring(0, GroupClassifier.ID_LENGTH)));
        assertThat(enricher.enrich(AWARDS)).isEqualTo(new Result(2, 0, 0));
        assertThat(claude.requests()).hasSize(2);
    }

    @Test
    void skipsDeletedAwardsAndAwardsWithoutADescription() {
        saveAward(jdbc, "CONT_AWD_DELETED", 2, "0.00", true);
        saveAward(jdbc, "CONT_AWD_UNDESCRIBED", 1, "1000.00", false);
        describe(jdbc, SHARED, "CONT_AWD_DELETED");

        assertThat(enricher.enrich(List.of("CONT_AWD_DELETED", "CONT_AWD_UNDESCRIBED")))
                .isEqualTo(new Result(0, 0, 0));
        assertThat(claude.requests()).isEmpty();
    }
}
