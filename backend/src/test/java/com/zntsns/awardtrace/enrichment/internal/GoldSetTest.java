package com.zntsns.awardtrace.enrichment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldSetTest {

    @TempDir
    Path dir;

    @Test
    void readsEveryLabeledRow() throws IOException {
        Path gold = csv("""
                description_hash,description,psc_code,label
                %s,"ENGINE TYPE 6 WITH CREW, CEDAR CREEK",F003,NATURAL_RESOURCES
                %s,SEE SCHEDULE,,UNCLASSIFIABLE
                """.formatted("a".repeat(64), "b".repeat(64)));

        assertThat(GoldSet.read(gold)).containsExactly(
                new GoldSet.Row("a".repeat(64), "ENGINE TYPE 6 WITH CREW, CEDAR CREEK", "F003", "NATURAL_RESOURCES"),
                new GoldSet.Row("b".repeat(64), "SEE SCHEDULE", null, "UNCLASSIFIABLE"));
    }

    @Test
    void refusesASetWithAnUnlabeledRow() throws IOException {
        Path gold = csv("""
                description_hash,description,psc_code,label
                %s,SEE SCHEDULE,,UNCLASSIFIABLE
                %s,JANITORIAL SERVICES,S201,
                """.formatted("a".repeat(64), "b".repeat(64)));

        assertThatThrownBy(() -> GoldSet.read(gold)).hasMessageContaining("Row 2").hasMessageContaining("no label");
    }

    @Test
    void refusesALabelThatIsNotACategoryCode() throws IOException {
        Path gold = csv("""
                description_hash,description,psc_code,label
                %s,JANITORIAL SERVICES,S201,Construction and facilities
                """.formatted("a".repeat(64)));

        assertThatThrownBy(() -> GoldSet.read(gold)).hasMessageContaining("label Construction and facilities");
    }

    private Path csv(String content) throws IOException {
        return Files.writeString(dir.resolve("gold.csv"), content);
    }
}
