package com.zntsns.awardtrace.enrichment.internal;

import static com.zntsns.awardtrace.AwardRows.saveAward;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.enrichment.internal.ClassificationStore.Result;
import java.math.BigDecimal;
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
@Import(TestcontainersConfiguration.class)
class ClassificationStoreIT {

    private static final String SHARED = "5".repeat(64);
    private static final String OTHER = "9".repeat(64);

    @Autowired
    ClassificationStore store;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void emptyTables() {
        AwardRows.emptyTables(jdbc);
    }

    @Test
    void announcesEveryLiveAwardWhoseDescriptionWasClassified() {
        saveAward(jdbc, "CONT_AWD_FIRST", 3, "1000.00", false);
        saveAward(jdbc, "CONT_AWD_SECOND", 7, "2000.00", false);
        saveAward(jdbc, "CONT_AWD_DELETED", 2, "0.00", true);
        saveAward(jdbc, "CONT_AWD_UNRELATED", 1, "500.00", false);
        describe(SHARED, "CONT_AWD_FIRST", "CONT_AWD_SECOND", "CONT_AWD_DELETED");
        describe(OTHER, "CONT_AWD_UNRELATED");

        Result result = store.store(List.of(classification(SHARED, "NATURAL_RESOURCES", "0.91", null)));

        assertThat(result).isEqualTo(new Result(1, 2));
        assertThat(outbox()).containsExactlyInAnyOrder(
                "CONT_AWD_FIRST CLASSIFICATION 4", "CONT_AWD_SECOND CLASSIFICATION 8");
        assertThat(jdbc.sql("""
                SELECT category || ' ' || confidence || ' ' || model || ' ' || prompt_version
                FROM classification WHERE description_hash = :hash
                """)
                .param("hash", SHARED)
                .query(String.class)
                .single())
                .isEqualTo("NATURAL_RESOURCES 0.91 claude-haiku-4-5 v1");
    }

    @Test
    void announcesNothingWhenTheSameClassificationIsStoredAgain() {
        saveAward(jdbc, "CONT_AWD_FIRST", 3, "1000.00", false);
        describe(SHARED, "CONT_AWD_FIRST");
        var classification = classification(SHARED, "NATURAL_RESOURCES", "0.91", null);

        store.store(List.of(classification));

        assertThat(store.store(List.of(classification))).isEqualTo(new Result(0, 0));
        assertThat(outbox()).containsExactly("CONT_AWD_FIRST CLASSIFICATION 4");
    }

    @Test
    void replacesAFailedClassificationWhenARetryAnswers() {
        saveAward(jdbc, "CONT_AWD_FIRST", 3, "1000.00", false);
        describe(SHARED, "CONT_AWD_FIRST");
        store.store(List.of(classification(SHARED, "UNCLASSIFIABLE", null, "FAILED")));

        Result result = store.store(List.of(classification(SHARED, "CONSTRUCTION_FACILITIES", "0.80", null)));

        assertThat(result).isEqualTo(new Result(1, 1));
        assertThat(jdbc.sql("SELECT category || ' ' || coalesce(reason_code, '-') FROM classification")
                .query(String.class)
                .single())
                .isEqualTo("CONSTRUCTION_FACILITIES -");
        assertThat(outbox()).containsExactly("CONT_AWD_FIRST CLASSIFICATION 4", "CONT_AWD_FIRST CLASSIFICATION 5");
    }

    @Test
    void storesADescriptionNoAwardCarriesYet() {
        assertThat(store.store(List.of(classification(OTHER, "OTHER", "0.55", null)))).isEqualTo(new Result(1, 0));
        assertThat(outbox()).isEmpty();
    }

    private void describe(String descriptionHash, String... awardIds) {
        jdbc.sql("UPDATE award SET description_hash = :hash WHERE award_id IN (:awardIds)")
                .param("hash", descriptionHash)
                .param("awardIds", List.of(awardIds))
                .update();
    }

    private List<String> outbox() {
        return jdbc.sql("SELECT aggregate_id || ' ' || change_reason || ' ' || index_version FROM outbox ORDER BY id")
                .query(String.class)
                .list();
    }

    private static Classification classification(String hash, String category, String confidence, String reason) {
        return new Classification(hash, category, confidence == null ? null : new BigDecimal(confidence), reason,
                "claude-haiku-4-5", "v1");
    }
}
