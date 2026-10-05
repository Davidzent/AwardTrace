package com.zntsns.awardtrace.enrichment.internal;

import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Usage;
import java.math.BigDecimal;

/**
 * Published Claude API prices, in US dollars per million tokens (doc 09), for the models the enricher may call. Cache
 * writes are the 5-minute kind the requests ask for. Message Batches halve every price.
 */
record Prices(BigDecimal input, BigDecimal output, BigDecimal cacheWrite, BigDecimal cacheRead) {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    /** Another model needs its prices checked and added here first. */
    static Prices of(String model) {
        return switch (model) {
            case "claude-haiku-4-5" -> perMillion("1.00", "5.00", "1.25", "0.10");
            case "claude-opus-5-5" -> perMillion("4.00", "20.00", "5.00", "0.20");
            case null, default -> throw new IllegalArgumentException("No prices for model " + model);
        };
    }

    BigDecimal cost(Usage usage) {
        return input.multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(output.multiply(BigDecimal.valueOf(usage.outputTokens())))
                .add(cacheWrite.multiply(BigDecimal.valueOf(usage.cacheWriteTokens())))
                .add(cacheRead.multiply(BigDecimal.valueOf(usage.cacheReadTokens())))
                .divide(MILLION);
    }

    /** What a request costs through a Message Batch, which halves every price. */
    BigDecimal batchCost(Usage usage) {
        return cost(usage).divide(BigDecimal.TWO);
    }

    private static Prices perMillion(String input, String output, String cacheWrite, String cacheRead) {
        return new Prices(new BigDecimal(input), new BigDecimal(output), new BigDecimal(cacheWrite),
                new BigDecimal(cacheRead));
    }
}
