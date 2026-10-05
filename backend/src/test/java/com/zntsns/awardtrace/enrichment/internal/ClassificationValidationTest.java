package com.zntsns.awardtrace.enrichment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.enrichment.internal.ClassificationValidation.Answer;
import com.zntsns.awardtrace.enrichment.internal.ClassificationValidation.Rejected;
import com.zntsns.awardtrace.enrichment.internal.ClassificationValidation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClassificationValidationTest {

    private static final List<String> SENT = List.of("aaaaaaaaaaaa", "bbbbbbbbbbbb");

    @Test
    void acceptsOneAnswerForEverySentId() {
        var checked = ClassificationValidation.check("""
                {"items": [{"id": "bbbbbbbbbbbb", "category": "UNCLASSIFIABLE", "confidence": 0.3},
                           {"id": "aaaaaaaaaaaa", "category": "NATURAL_RESOURCES", "confidence": 0.915}]}""", SENT);

        assertThat(checked).isEqualTo(new Valid(Map.of(
                "aaaaaaaaaaaa", new Answer("NATURAL_RESOURCES", new BigDecimal("0.92")),
                "bbbbbbbbbbbb", new Answer("UNCLASSIFIABLE", new BigDecimal("0.30")))));
    }

    @Test
    void rejectsAnAnswerMissingAnId() {
        assertThat(check("""
                {"items": [{"id": "aaaaaaaaaaaa", "category": "OTHER", "confidence": 0.9}]}"""))
                .isEqualTo("MISSING_ID");
    }

    @Test
    void rejectsAnAnswerWithAnIdThatWasNotSent() {
        assertThat(check("""
                {"items": [{"id": "aaaaaaaaaaaa", "category": "OTHER", "confidence": 0.9},
                           {"id": "bbbbbbbbbbbb", "category": "OTHER", "confidence": 0.9},
                           {"id": "cccccccccccc", "category": "OTHER", "confidence": 0.9}]}"""))
                .isEqualTo("UNKNOWN_ID");
    }

    @Test
    void rejectsAnAnswerThatRepeatsAnId() {
        assertThat(check("""
                {"items": [{"id": "aaaaaaaaaaaa", "category": "OTHER", "confidence": 0.9},
                           {"id": "aaaaaaaaaaaa", "category": "OTHER", "confidence": 0.9}]}"""))
                .isEqualTo("REPEATED_ID");
    }

    @Test
    void rejectsAnAnswerWithAnUnknownCategory() {
        assertThat(check("""
                {"items": [{"id": "aaaaaaaaaaaa", "category": "OTHER", "confidence": 0.9},
                           {"id": "bbbbbbbbbbbb", "category": "AGRICULTURE", "confidence": 0.9}]}"""))
                .isEqualTo("UNKNOWN_CATEGORY");
    }

    @Test
    void rejectsAConfidenceOutsideZeroToOne() {
        assertThat(check("""
                {"items": [{"id": "aaaaaaaaaaaa", "category": "OTHER", "confidence": 0.9},
                           {"id": "bbbbbbbbbbbb", "category": "OTHER", "confidence": 1.2}]}"""))
                .isEqualTo("INVALID_CONFIDENCE");
        assertThat(check("""
                {"items": [{"id": "aaaaaaaaaaaa", "category": "OTHER", "confidence": -0.1},
                           {"id": "bbbbbbbbbbbb", "category": "OTHER", "confidence": 0.9}]}"""))
                .isEqualTo("INVALID_CONFIDENCE");
    }

    @Test
    void rejectsAnAnswerThatIsNotTheSchemasShape() {
        assertThat(check("{\"items\": [{\"id\": \"aaaaaaaaaaaa\", \"category\": \"OTHER\"")).isEqualTo("MALFORMED");
        assertThat(check("")).isEqualTo("MALFORMED");
        assertThat(check("{}")).isEqualTo("MALFORMED");
        assertThat(check("""
                {"items": [{"id": "aaaaaaaaaaaa", "category": "OTHER"},
                           {"id": "bbbbbbbbbbbb", "category": "OTHER", "confidence": 0.9}]}"""))
                .isEqualTo("MALFORMED");
    }

    private static String check(String json) {
        return ((Rejected) ClassificationValidation.check(json, SENT)).problem();
    }
}
