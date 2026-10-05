package com.zntsns.awardtrace.enrichment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("enricher")
@Import(TestcontainersConfiguration.class)
class ClassifyPromptIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EnrichmentProperties properties;

    /** Doc 09: each definition is the exact text the prompt uses, so the configured prompt must match the table. */
    @Test
    void definesEachCategoryExactlyAsTheTaxonomyTableDoes() throws IOException {
        String prompt;
        try (InputStream in = getClass().getResourceAsStream(
                "/prompts/classify-" + properties.promptVersion() + ".txt")) {
            prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        var rows = jdbc.sql("""
                SELECT '| ' || code || ' | ' || definition || ' |' FROM taxonomy_category ORDER BY sort_order
                """)
                .query(String.class)
                .list();

        assertThat(rows)
                .hasSize(ClassificationValidation.CATEGORIES.size())
                .allSatisfy(row -> assertThat(prompt).contains(row + "\n"));
    }
}
