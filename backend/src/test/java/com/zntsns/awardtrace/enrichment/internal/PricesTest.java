package com.zntsns.awardtrace.enrichment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Usage;
import org.junit.jupiter.api.Test;

class PricesTest {

    @Test
    void pricesEachKindOfTokenPerMillion() {
        var haiku = Prices.of("claude-haiku-4-5");

        assertThat(haiku.cost(new Usage(1_000_000, 0, 0, 0))).isEqualByComparingTo("1.00");
        assertThat(haiku.cost(new Usage(0, 1_000_000, 0, 0))).isEqualByComparingTo("5.00");
        assertThat(haiku.cost(new Usage(0, 0, 1_000_000, 0))).isEqualByComparingTo("1.25");
        assertThat(haiku.cost(new Usage(0, 0, 0, 1_000_000))).isEqualByComparingTo("0.10");
    }

    @Test
    void addsUpOneRequest() {
        // 950 input and 30 output tokens on Haiku 4.5: $0.00095 + $0.00015.
        assertThat(Prices.of("claude-haiku-4-5").cost(new Usage(950, 30, 0, 0))).isEqualByComparingTo("0.0011");
        // 300 input, 410 output with thinking, and 600 written to the cache on Opus 5.5: $0.0012 + $0.0082 + $0.003.
        assertThat(Prices.of("claude-opus-5-5").cost(new Usage(300, 410, 600, 0))).isEqualByComparingTo("0.0124");
    }

    @Test
    void refusesAModelWithoutPublishedPrices() {
        assertThatThrownBy(() -> Prices.of("claude-sonnet-5-5")).hasMessageContaining("claude-sonnet-5-5");
    }
}
