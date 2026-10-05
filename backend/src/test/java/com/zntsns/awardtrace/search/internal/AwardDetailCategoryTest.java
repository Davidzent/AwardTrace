package com.zntsns.awardtrace.search.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.award.AwardQueries.ModelCategory;
import com.zntsns.awardtrace.search.internal.AwardDetail.CategoryDetail;
import java.math.BigDecimal;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Which category an award's detail shows, the same rule the indexer applies (CategorySource). */
class AwardDetailCategoryTest {

    private static final Function<String, String> LABELS = code -> "label of " + code;
    private static final ModelCategory SOFTWARE =
            new ModelCategory("IT_SOFTWARE", new BigDecimal("0.91"), null, "claude-haiku-4-5", "v1");
    private static final ModelCategory VAGUE =
            new ModelCategory("UNCLASSIFIABLE", new BigDecimal("0.40"), "VAGUE", "claude-haiku-4-5", "v1");
    private static final CategoryDetail BASELINE = new CategoryDetail("CYBERSECURITY", "label of CYBERSECURITY",
            "baseline", null, null, null, "CYBERSECURITY", "label of CYBERSECURITY");

    @Test
    void showsThePscBaselineWhileItIsTheDefault() {
        assertThat(AwardDetail.category(SOFTWARE, "CYBERSECURITY", false, LABELS)).isEqualTo(BASELINE);
    }

    @Test
    void showsTheClassifiersCategoryBesideTheBaselineWhenItIsTheDefault() {
        assertThat(AwardDetail.category(SOFTWARE, "CYBERSECURITY", true, LABELS)).isEqualTo(new CategoryDetail(
                "IT_SOFTWARE", "label of IT_SOFTWARE", "llm", 0.91, "claude-haiku-4-5", "v1", "CYBERSECURITY",
                "label of CYBERSECURITY"));
    }

    @Test
    void keepsTheBaselineForADescriptionTheClassifierCouldNotPlace() {
        assertThat(AwardDetail.category(VAGUE, "CYBERSECURITY", true, LABELS)).isEqualTo(BASELINE);
    }

    @Test
    void givesAnAwardWithoutAPscTheClassifiersCategory() {
        assertThat(AwardDetail.category(SOFTWARE, null, true, LABELS)).isEqualTo(new CategoryDetail("IT_SOFTWARE",
                "label of IT_SOFTWARE", "llm", 0.91, "claude-haiku-4-5", "v1", null, null));
    }

    @Test
    void hasNoCategoryWithoutEitherSource() {
        assertThat(AwardDetail.category(null, null, true, LABELS)).isNull();
        assertThat(AwardDetail.category(VAGUE, null, true, LABELS)).isNull();
    }
}
