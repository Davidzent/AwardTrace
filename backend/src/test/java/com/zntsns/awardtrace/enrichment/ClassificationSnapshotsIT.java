package com.zntsns.awardtrace.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.enrichment.ClassificationSnapshots.Snapshot;
import com.zntsns.awardtrace.enrichment.internal.FakeClaudeConfiguration;
import com.zntsns.awardtrace.shared.S3Config.S3Properties;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

@SpringBootTest
@ActiveProfiles("enricher")
@Import({TestcontainersConfiguration.class, FakeClaudeConfiguration.class})
class ClassificationSnapshotsIT {

    @Autowired
    ClassificationSnapshots snapshots;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    S3Client s3;

    @Autowired
    S3Properties s3Properties;

    /** A category, a vague description with its confidence, and a failure without one. */
    @BeforeEach
    void classifyThreeDescriptions() {
        classify("1", "NATURAL_RESOURCES", "0.91", null);
        classify("2", "UNCLASSIFIABLE", "0.40", "VAGUE");
        classify("3", "UNCLASSIFIABLE", null, "FAILED");
    }

    @AfterEach
    void emptyTableAndFolder() {
        AwardRows.emptyTables(jdbc);
        for (String key : snapshotKeys()) {
            s3.deleteObject(request -> request.bucket(s3Properties.bucket()).key(key));
        }
    }

    @Test
    void restoresEveryClassificationExactlyAsItWas() throws IOException {
        String before = table();
        Snapshot snapshot = snapshots.write();
        jdbc.sql("TRUNCATE classification").update();

        assertThat(snapshots.restore()).isEqualTo(3);

        assertThat(snapshot.key()).startsWith(ClassificationSnapshots.FOLDER).endsWith(".csv.gz");
        assertThat(snapshot.classifications()).isEqualTo(3);
        assertThat(table()).isEqualTo(before);
    }

    @Test
    void uploadsNothingNewWhileTheTableIsUnchanged() throws IOException {
        Snapshot first = snapshots.write();

        assertThat(snapshots.write()).isEqualTo(first);
        assertThat(snapshotKeys()).containsExactly(first.key());

        classify("4", "OTHER", "0.75", null);
        assertThat(snapshots.write().classifications()).isEqualTo(4);
        assertThat(snapshotKeys()).hasSize(2);
    }

    @Test
    void refusesToHideAFullerSnapshotBehindOneWithFewerClassifications() throws IOException {
        Snapshot full = snapshots.write();
        jdbc.sql("DELETE FROM classification WHERE description_hash = repeat('3', 64)").update();

        assertThatThrownBy(snapshots::write)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restore before writing");
        assertThat(snapshotKeys()).containsExactly(full.key());
    }

    @Test
    void keepsWhatTheTableAlreadyHoldsAndRestoresNothingWithoutASnapshot() throws IOException {
        assertThat(snapshots.restore()).isZero();

        snapshots.write();
        jdbc.sql("UPDATE classification SET category = 'OTHER' WHERE description_hash = repeat('1', 64)").update();

        assertThat(snapshots.restore()).isZero();
        assertThat(jdbc.sql("SELECT category FROM classification WHERE description_hash = repeat('1', 64)")
                .query(String.class)
                .single())
                .isEqualTo("OTHER");
    }

    private void classify(String hashDigit, String category, String confidence, String reasonCode) {
        jdbc.sql("""
                INSERT INTO classification (description_hash, category, confidence, reason_code, model, prompt_version,
                                            classified_at)
                VALUES (repeat(:digit, 64), :category, CAST(:confidence AS numeric), CAST(:reasonCode AS text),
                        'claude-haiku-4-5', 'v1', TIMESTAMPTZ '2026-10-05 03:15:42.123456+00')
                """)
                .param("digit", hashDigit)
                .param("category", category)
                .param("confidence", confidence)
                .param("reasonCode", reasonCode)
                .update();
    }

    /** Every row, every column, in one string. */
    private String table() {
        return jdbc.sql("SELECT string_agg(to_jsonb(c)::text, ',' ORDER BY description_hash) FROM classification c")
                .query(String.class)
                .single();
    }

    private List<String> snapshotKeys() {
        return s3.listObjectsV2Paginator(request -> request.bucket(s3Properties.bucket())
                        .prefix(ClassificationSnapshots.FOLDER))
                .contents().stream()
                .map(S3Object::key)
                .sorted()
                .toList();
    }
}
